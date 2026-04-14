# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 4-based poker server simulating a private home poker game. Java 25 with a Gradle multi-module build.

## Build & Run

```bash
# Build all modules
./gradlew clean build

# Run the server (requires MongoDB)
./gradlew :poker-server:bootRun

# Start MongoDB via Docker Compose
docker-compose up
```

## Testing

```bash
# Run all tests
./gradlew clean test

# Run tests for a specific module
./gradlew :poker-server:test
./gradlew :poker-common:test

# Run a specific test class
./gradlew :poker-server:test --tests "org.homepoker.poker.ClassicPokerRankerTest"

# Run a specific test method
./gradlew :poker-server:test --tests "org.homepoker.poker.ClassicPokerRankerTest.testFiveCardHandResults"
```

Integration tests use TestContainers (automatic MongoDB container) via `BaseIntegrationTest`.

## Module Structure

**poker-common** — Shared models between server and client:
- `model/command/` — Game commands (`JoinGame`, `LeaveGame`, `StartGame`, `BuyIn`, `PlayerActionCommand`, `TableCommand`, etc.)
- `model/event/` — Game events (`PokerEvent`, `UserEvent`, `TableEvent`, etc.)
- `model/game/` — Core game models (`Table`, `Seat`, `PlayerAction`, `GameCriteria`)
- `model/game/cash/` — Cash game models (`CashGame`, `CashGameDetails`)
- `model/poker/` — Card models (`Card`, `CardSuit`, `CardValue`)
- `model/user/` — User models (`User`, `UserLogin`, `UserRole`)

**poker-server** — Main application:
- `game/` — Game management; `GameManager<T>` abstract base, `CashGameManager`, `TexasHoldemTableManager`
- `poker/` — Hand ranking engine; `BitwisePokerRanker` is the high-performance implementation
- `security/` — JWT auth with Spring Security (`JwtTokenService`, `JwtAuthenticationFilter`)
- `rest/` — REST controllers (`AuthenticationController`, `UserController`, `CashGameController`)
- `websocket/` — WebSocket handler for real-time game updates
- `user/` — User management with MongoDB (`UserManager`, `UserRepository`)
- `threading/` — `VirtualThreadManager` wrapping Java 21 virtual threads and single-thread debug mode

## Key Architecture Patterns

**Command/Game Loop Model:** Game state is mutated exclusively by a single game loop thread. All external inputs are submitted as commands to a JCTools MPSC (Multi-Producer, Single-Consumer) lock-free queue. This eliminates synchronization concerns in game logic.

**Threading Modes:** Controlled by `GameServerProperties`. In tests, `threadModel: SINGLE_THREAD` and `gameLoopIntervalMilliseconds: 0` are used for deterministic behavior (see `application-test.yml`).

**Event System:** Game events flow from game logic outward to `GameListener` / `UserGameListener` implementations, decoupling game state from delivery concerns.

**Extensibility:** `GameManager<T>` is generic over the game type, enabling cash games and tournaments to share the command/event infrastructure.

**Multi-table Support:** A GameManager manages the top level game state and allows multiple tables to be created to acocomodate more players. Each table has its own game loop and state.

## Game and Table State Machine

** See `cash-game-state-management.md` for details on the game and table state and game flow. **

## Configuration

- `poker-server/src/main/resources/application.yml` — Production config (logging, JWT expiration, admin users, registration passcode)
- `poker-server/src/test/resources/application-test.yml` — Test overrides (single-thread mode, test admin users)

Main package: `org.homepoker`

## Testing Conventions

Game-loop tests use `application-test.yml` which sets `threadModel: SINGLE_THREAD` and `gameLoopIntervalMilliseconds: 0`. This means commands submitted to a `GameManager` are processed deterministically on the test thread — you can submit a command, tick the loop, and assert state/events synchronously. No sleeps, no awaits.

Integration tests extend `BaseIntegrationTest` (TestContainers MongoDB). Prefer this over mocking the repository layer.

## Security-Critical Invariants

**Never expose another player's hole cards or intents.** Table/seat state sent to a user must strip `cards` and `intent` from other players' seats. Any new event or DTO touching seat data must preserve this — add a test that asserts it.

**Admin debug-view exception.** A server-side debug flag (default **off**) may permit admin users — and only admin users — to receive all hole cards. When this flag is on, the server must emit a broadcast event warning **all connected clients** that admins can see all cards. The flag must not be togglable without that broadcast, and with the flag off an admin gets no more visibility than any other player.

## Skills

Project-specific skills live in `.claude/skills/`. Prefer invoking them over re-reading the full design doc:
- `game-state` — game-level state machine work (GameStatus, table balancing, pause)
- `table-state` — table-level hand progression (HandPhase, betting rounds, side pots)
- `create-command` — scaffold a new command end-to-end
- `add-event-type` — scaffold a new event end-to-end
- `test-game-scenario` — write a deterministic game-loop test
