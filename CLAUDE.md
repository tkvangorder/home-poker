# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot-based poker server simulating a private home poker game. Java 21 with Maven multi-module build.

## Build & Run

```bash
# Build all modules
./mvnw clean install

# Run the server (requires MongoDB)
./mvnw spring-boot:run -pl poker-server

# Start MongoDB via Docker Compose
docker-compose up
```

## Testing

```bash
# Run all tests
./mvnw clean test

# Run tests for a specific module
./mvnw clean test -pl poker-server
./mvnw clean test -pl poker-common

# Run a specific test class
./mvnw clean test -pl poker-server -Dtest=ClassicPokerRankerTest

# Run a specific test method
./mvnw clean test -pl poker-server -Dtest=ClassicPokerRankerTest#testFiveCardHandResults
```

Integration tests use TestContainers (automatic MongoDB container) via `BaseIntegrationTest`.

## Module Structure

**poker-common** — Shared models between server and client:
- `model/command/` — Game commands (`RegisterForGame`, `UnregisterFromGame`, `TableCommand`, etc.)
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

## Configuration

- `poker-server/src/main/resources/application.yml` — Production config (logging, JWT expiration, admin users, registration passcode)
- `poker-server/src/test/resources/application-test.yml` — Test overrides (single-thread mode, test admin users)

Main package: `org.homepoker`