# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 4-based poker server simulating a private home poker game, plus an Angular browser client. Java 25 with a Gradle multi-module build. Main package: `org.homepoker`.

## Build & Run

```bash
# Build all modules (Java + Angular client; downloads Node 24 on first run)
./gradlew clean build

# Run the server with the compiled client served from http://localhost:8080 (requires MongoDB)
./gradlew :poker-server:bootRun

# Client dev loop: run the server as above, then in a second terminal
cd poker-client-angular && npm start        # http://localhost:4200, proxies API + WebSocket to :8080

# Start MongoDB via Docker Compose
docker-compose up
```

## Testing

```bash
# Run all Java tests (Jest runs under check/build, see npmTest below)
./gradlew clean test

# Run tests for a specific module
./gradlew :poker-server:test
./gradlew :poker-common:test
./gradlew :poker-client-angular:npmTest     # Angular unit tests (Jest) through Gradle
cd poker-client-angular && npm test         # same tests, direct

# Run a specific test class / method
./gradlew :poker-server:test --tests "org.homepoker.poker.ClassicPokerRankerTest"
./gradlew :poker-server:test --tests "org.homepoker.poker.ClassicPokerRankerTest.testFiveCardHandResults"
```

**Game-loop tests** use the in-memory fixtures in `poker-server/src/test/java/org/homepoker/test/` (`GameManagerTestFixture`, `SplitPotScenarioFixture`, `DeckBuilder`). These need no Spring context or database. Submit a command, call `tick()`, and assert on state and captured events synchronously. No sleeps, no awaits. The `test-game-scenario` skill has the details.

**Integration tests** extend `BaseIntegrationTest` (TestContainers MongoDB, `test` profile). Prefer this over mocking the repository layer. `application-test.yml` puts the game loop in `SINGLE_THREAD` mode with a 0 ms interval, so these are deterministic too.

## Module Structure

**poker-common** — Shared models:
- `model/command/` — Commands (`JoinGame`, `LeaveGame`, `StartGame`, `BuyIn`, `PlayerActionCommand`, `TableCommand`, etc.)
- `model/event/` — Events: `PokerEvent`, `GameEvent`, `TableEvent`, `UserEvent`, with concrete records in `game/`, `table/`, `user/`
- `model/game/` — Core game models (`Table`, `Seat`, `Player`, `PlayerAction`, `HandPhase`, `GameStatus`)
- `model/game/cash/` — Cash game models (`CashGame`, `CashGameDetails`)
- `model/poker/` — Card models (`Card`, `CardSuit`, `CardValue`)
- `model/user/` — User models (`User`, `UserLogin`, `UserRole`)

**poker-server** — Main application:
- `game/` — Game management: `GameManager<T>` abstract base, `CashGameManager`, `table/TexasHoldemTableManager`
- `poker/` — Hand ranking (`BitwisePokerRanker` is the one used in production) and `Deck`
- `recording/` — Event store for admin hand replay (`EventRecorder`, `EventRecorderService`)
- `security/` — JWT auth with Spring Security (`JwtTokenService`, `JwtAuthenticationFilter`, `WebSecurityConfiguration`)
- `rest/` — REST controllers (`AuthenticationController`, `UserController`, `CashGameController`, `ReplayController`, `SpaForwardingController`)
- `websocket/` — WebSocket handler for real-time game updates
- `user/` — User management with MongoDB (`UserManager`, `UserRepository`)
- `threading/` — `VirtualThreadManager` (virtual threads, plus the single-thread mode used for debugging and tests)

**poker-client-angular** — Angular 21 browser client. See `poker-client-angular/CLAUDE.md` for client conventions.
- Built by Gradle via the node plugin. `:poker-server:bootJar` and `bootRun` copy its `dist/.../browser` output into `static/`.
- `:poker-server:test` never builds the client.
- **Adding a client route:** also add it to `SpaForwardingController` (so a browser refresh serves `index.html`) and to the GET `permitAll` list in `WebSecurityConfiguration`. `ng serve` won't reveal a missing entry.

## Key Architecture Patterns

**Command/Game Loop Model:** Game state is mutated exclusively by a single game-loop thread per game. All external inputs are submitted as commands to a JCTools MPSC (Multi-Producer, Single-Consumer) lock-free queue. This eliminates synchronization concerns in game logic.

**Multi-table Support:** A `GameManager` owns the game-level state and one `TableManager` per table. Each tick it drains the command queue, routes `TableCommand`s to the right `TableManager` by `tableId`, and calls `transitionTable()` on every table. Tables keep their own state but share the game's loop thread.

**Threading Modes:** Controlled by `GameServerProperties` (`threadModel`, `gameLoopIntervalMilliseconds`).

**Event System:** Game logic queues events with `gameContext.queueEvent(...)`. After each tick they are stamped with sequence numbers and fanned out to `GameListener` / `UserGameListener` implementations (WebSocket delivery, the event recorder). This keeps game state decoupled from delivery.

**Extensibility:** `GameManager<T>` is generic over the game type, so cash games and tournaments can share the command/event infrastructure.

## Design Docs

- `cash-game-state-management.md` — game- and table-level state machines (`GameStatus`, `HandPhase`), betting rounds, side pots, table balancing. Read it before state machine work, alongside `GameManager` and `TexasHoldemTableManager`.
- `design-decisions.md` — the why behind shipped features: event sequence numbers, presence and disconnect eviction, the event store and admin replay, showdown winning cards, deterministic decks, the combined build, and the Phaser table. Read the relevant section before changing one of those areas, and update it when a design decision changes.

## Configuration

- `poker-server/src/main/resources/application.yml` — Production config (logging, JWT expiration, admin users, registration passcode). A hook asks for confirmation before any edit to this file.
- `poker-server/src/test/resources/application-test.yml` — Test overrides (single-thread mode, test admin users)

## Security-Critical Invariants

**Never expose another player's hole cards or intents.** Table/seat state sent to a user must strip `cards` and `pendingIntent` from other players' seats (see `TableManager.sanitizeTable()`). Private per-player data must travel in a `UserEvent`. Any new event or DTO touching seat data must preserve this, and must come with a test that asserts it. The `hole-card-auditor` agent audits these paths.

**Admin visibility must be announced.** Any feature that lets an admin see hidden cards must broadcast a warning to all connected clients. Today that means admin hand replay: viewing a hand from a game that isn't finished emits `AdminViewingReplay`.

**Admin debug-view flag (not yet implemented).** If a server-side flag is ever added to let admins, and only admins, see all hole cards live:
- It must default to **off**.
- Turning it on must broadcast a warning to **all connected clients** that admins can see all cards; it must not be possible to enable it without that broadcast.
- With the flag off, an admin gets no more visibility than any other player.

## Command & Event Spec

`poker-server/src/main/resources/static/command-event-spec.md` is the canonical client-facing reference for every command and event (field names, types, and `eventType` values).

**ALWAYS update `command-event-spec.md` in the same change whenever you add, remove, or modify a command or event.** This includes adding, renaming, or removing a field on any command, event, or their nested records (e.g., `ShowdownResult.Winner`). The spec and the classes under `model/command/` + `model/event/` must never drift. The `create-command` and `add-event-type` skills include this step.

## Skills & Agents

Project-specific skills live in `.claude/skills/`:
- `create-command` — scaffold a new command end-to-end
- `add-event-type` — scaffold a new event end-to-end (includes the hole-card privacy check)
- `test-game-scenario` — write a deterministic game-loop test using the in-memory fixtures
- `phaser-dev` — general Phaser 3 reference for the client's table renderer

Agent in `.claude/agents/`:
- `hole-card-auditor` — run after changes to seat/table serialization, WebSocket push, REST responses, or event definitions
