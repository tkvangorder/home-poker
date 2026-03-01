# Command & Event Specification

This document catalogs all commands and events in the poker server. It serves as the contract between server and client.

## Conventions

- **Commands** are submitted by clients (via WebSocket or REST) to request state changes.
- **Events** are emitted by the server to notify clients of state changes.
- All commands use a `commandId` JSON discriminator derived from the class name in kebab-case (e.g., `RegisterForGame` -> `register-for-game`).
- All events use an `eventType` JSON discriminator derived from the class name in kebab-case (e.g., `HandStarted` -> `hand-started`).
- The `user` field on commands is **never serialized** — it is injected server-side from the authenticated session.

---

## Commands

### Game-Level Commands

Game-level commands affect game-wide state (registration, lifecycle, chips). They implement `GameCommand` and are routed through `GameManager.applyCommand()`.

#### RegisterForGame

Registers a player for the game before it starts.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `register-for-game`
**Accepted in states:** SCHEDULED, SEATING

---

#### UnregisterFromGame

Removes a player's registration before the game starts.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `unregister-from-game`
**Accepted in states:** SCHEDULED, SEATING

---

#### StartGame

Admin command to transition the game from SEATING to ACTIVE.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `start-game`
**Accepted in states:** SEATING
**Requires:** Admin role, 2+ seated players, current time >= start time

---

#### PauseGame

Admin command to pause the game after current hands complete (two-phase pause).

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `pause-game`
**Accepted in states:** ACTIVE

---

#### ResumeGame

Admin command to resume the game from PAUSED state.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `resume-game`
**Accepted in states:** PAUSED

---

#### EndGame

Admin command to end the game after current hands complete.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `end-game`
**Accepted in states:** SEATING, ACTIVE, PAUSED

---

#### BuyIn

Player buys chips into the game.

| Field    | Type   | Description          |
|----------|--------|----------------------|
| `gameId` | String | Target game ID       |
| `user`   | User   | (server-injected)    |
| `amount` | int    | Chip amount to buy   |

**commandId:** `buy-in`
**Accepted in states:** SEATING, ACTIVE, PAUSED

---

#### LeaveGame

Player leaves the game. If a hand is in progress, the leave takes effect after the current hand.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `leave-game`
**Accepted in states:** SEATING, ACTIVE, PAUSED

---

### Table-Level Commands

Table-level commands target a specific table within a game. They implement `TableCommand` (which extends `GameCommand`) and include a `tableId` field. Commands are routed through either `TableManager.applyCommand()` (common) or a game-specific subclass like `TexasHoldemTableManager.applySubcommand()`.

#### PlayerActionCommand

Player submits a game action during their turn in a betting round.

| Field     | Type         | Description                                |
|-----------|--------------|--------------------------------------------|
| `gameId`  | String       | Target game ID                             |
| `tableId` | String       | Target table ID                            |
| `user`    | User         | (server-injected)                          |
| `action`  | PlayerAction | The action: Fold, Check, Call, Bet, Raise  |

**commandId:** `player-action-command`
**Accepted in phases:** PRE_FLOP_BETTING, FLOP_BETTING, TURN_BETTING, RIVER_BETTING
**Validation:** Must be the player's turn, action must be valid for current situation

**PlayerAction variants:**

| Action          | Fields           | Description                       |
|-----------------|------------------|-----------------------------------|
| `Fold`          | (none)           | Forfeit the hand                  |
| `Check`         | (none)           | Pass when no bet to match         |
| `Call(amount)`  | `amount` (int)   | Match the current bet             |
| `Bet(amount)`   | `amount` (int)   | Place a new bet (no existing bet) |
| `Raise(amount)` | `amount` (int)   | Raise the current bet             |

---

#### PlayerIntent

Player pre-selects their action before their turn arrives. Auto-applied when valid.

| Field     | Type         | Description                |
|-----------|--------------|----------------------------|
| `gameId`  | String       | Target game ID             |
| `tableId` | String       | Target table ID            |
| `user`    | User         | (server-injected)          |
| `action`  | PlayerAction | The intended action        |

**commandId:** `player-intent`
**Accepted in phases:** Any betting phase (before player's turn)

---

#### ShowCards

Player reveals their hole cards during the HAND_COMPLETE review period.

| Field     | Type   | Description       |
|-----------|--------|-------------------|
| `gameId`  | String | Target game ID    |
| `tableId` | String | Target table ID   |
| `user`    | User   | (server-injected) |

**commandId:** `show-cards`
**Accepted in phases:** HAND_COMPLETE (during review period)

---

#### PostBlind

New arrival posts a blind to enter play immediately instead of waiting for their natural blind position.

| Field     | Type   | Description       |
|-----------|--------|-------------------|
| `gameId`  | String | Target game ID    |
| `tableId` | String | Target table ID   |
| `user`    | User   | (server-injected) |

**commandId:** `post-blind`
**Accepted in phases:** PREDEAL

---

## Events

### Game-Level Events

Game-level events report changes to game-wide state. They implement `GameEvent`.

#### GameStatusChanged

The game transitioned between lifecycle states.

| Field       | Type       | Description                 |
|-------------|------------|-----------------------------|
| `timestamp` | Instant    | When the transition occurred|
| `gameId`    | String     | Game ID                     |
| `oldStatus` | GameStatus | Previous state              |
| `newStatus` | GameStatus | New state                   |

**eventType:** `game-status-changed`

**GameStatus values:** `SCHEDULED`, `SEATING`, `ACTIVE`, `PAUSED`, `COMPLETED`

---

#### GameMessage

Informational message broadcast to all game participants.

| Field       | Type    | Description      |
|-------------|---------|------------------|
| `timestamp` | Instant | When emitted     |
| `gameId`    | String  | Game ID          |
| `message`   | String  | Message content  |

**eventType:** `game-message`

---

#### PlayerBuyIn

A player bought chips.

| Field          | Type    | Description               |
|----------------|---------|---------------------------|
| `timestamp`    | Instant | When the buy-in occurred  |
| `gameId`       | String  | Game ID                   |
| `userId`       | String  | Player who bought in      |
| `amount`       | int     | Chips purchased           |
| `newChipCount` | int     | Player's total chip count |

**eventType:** `player-buy-in`

---

#### PlayerMoved

A player was reassigned to a different table (during table balancing).

| Field         | Type    | Description                  |
|---------------|---------|------------------------------|
| `timestamp`   | Instant | When the move occurred       |
| `gameId`      | String  | Game ID                      |
| `userId`      | String  | Player who was moved         |
| `fromTableId` | String? | Previous table (null if new) |
| `toTableId`   | String  | Destination table            |

**eventType:** `player-moved`

---

### Table-Level Events

Table-level events report changes within a specific table's hand. They implement `TableEvent`.

#### HandStarted

A new hand has begun.

| Field                | Type    | Description                 |
|----------------------|---------|-----------------------------|
| `timestamp`          | Instant | When the hand started       |
| `gameId`             | String  | Game ID                     |
| `tableId`            | String  | Table ID                    |
| `handNumber`         | int     | Monotonically increasing    |
| `dealerPosition`     | int     | Seat index of dealer        |
| `smallBlindPosition` | int     | Seat index of small blind   |
| `bigBlindPosition`   | int     | Seat index of big blind     |
| `smallBlindAmount`   | int     | Small blind chip amount     |
| `bigBlindAmount`     | int     | Big blind chip amount       |

**eventType:** `hand-started`

---

#### HoleCardsDealt

Hole cards dealt to a specific player. This is a **private event** — only the target player should see the card values.

| Field          | Type           | Description               |
|----------------|----------------|---------------------------|
| `timestamp`    | Instant        | When cards were dealt      |
| `gameId`       | String         | Game ID                    |
| `tableId`      | String         | Table ID                   |
| `userId`       | String         | Player receiving cards     |
| `seatPosition` | int            | Seat index                 |
| `cards`        | List<SeatCard> | The dealt cards            |

**eventType:** `hole-cards-dealt`
**Implements:** `TableEvent`, `UserEvent` (dual interface for privacy filtering)

---

#### CommunityCardsDealt

Community cards dealt (flop, turn, or river).

| Field       | Type       | Description                          |
|-------------|------------|--------------------------------------|
| `timestamp` | Instant    | When cards were dealt                |
| `gameId`    | String     | Game ID                              |
| `tableId`   | String     | Table ID                             |
| `cards`     | List<Card> | The dealt community cards            |
| `phase`     | String     | Which phase dealt them (FLOP, etc.)  |

**eventType:** `community-cards-dealt`

---

#### PlayerActed

A player performed an action during a betting round.

| Field          | Type         | Description                  |
|----------------|--------------|------------------------------|
| `timestamp`    | Instant      | When the action occurred     |
| `gameId`       | String       | Game ID                      |
| `tableId`      | String       | Table ID                     |
| `seatPosition` | int          | Seat index of the player     |
| `userId`       | String       | Player who acted             |
| `action`       | PlayerAction | The action taken             |
| `chipCount`    | int          | Player's remaining chips     |

**eventType:** `player-acted`

---

#### PlayerTimedOut

A player failed to act within the time limit; a default action was applied.

| Field           | Type         | Description                       |
|-----------------|--------------|-----------------------------------|
| `timestamp`     | Instant      | When the timeout occurred         |
| `gameId`        | String       | Game ID                           |
| `tableId`       | String       | Table ID                          |
| `seatPosition`  | int          | Seat index of the player          |
| `userId`        | String       | Player who timed out              |
| `defaultAction` | PlayerAction | The action applied (Check/Fold)   |

**eventType:** `player-timed-out`

---

#### BettingRoundComplete

All active players have acted; the betting round is finished.

| Field            | Type            | Description                          |
|------------------|-----------------|--------------------------------------|
| `timestamp`      | Instant         | When the round completed             |
| `gameId`         | String          | Game ID                              |
| `tableId`        | String          | Table ID                             |
| `completedPhase` | HandPhase       | Which betting phase completed        |
| `pots`           | List<Table.Pot> | Current pot state after collection   |

**eventType:** `betting-round-complete`

---

#### ShowdownResult

Hand reached showdown; winners determined and pots awarded.

| Field        | Type             | Description                    |
|--------------|------------------|--------------------------------|
| `timestamp`  | Instant          | When showdown resolved         |
| `gameId`     | String           | Game ID                        |
| `tableId`    | String           | Table ID                       |
| `potResults` | List<PotResult>  | Results for each pot           |

**eventType:** `showdown-result`

**PotResult fields:**

| Field       | Type          | Description              |
|-------------|---------------|--------------------------|
| `potIndex`  | int           | Pot number (0 = main)    |
| `potAmount` | int           | Total chips in the pot   |
| `winners`   | List<Winner>  | Winners of this pot      |

**Winner fields:**

| Field             | Type   | Description                    |
|-------------------|--------|--------------------------------|
| `seatPosition`    | int    | Winning player's seat index    |
| `userId`          | String | Winning player's ID            |
| `amount`          | int    | Chips awarded                  |
| `handDescription` | String | Winning hand (e.g., "Full House, Aces over Kings") |

---

#### HandComplete

The hand has finished; entering the review period.

| Field        | Type    | Description                  |
|--------------|---------|------------------------------|
| `timestamp`  | Instant | When the hand completed      |
| `gameId`     | String  | Game ID                      |
| `tableId`    | String  | Table ID                     |
| `handNumber` | int     | The completed hand number    |

**eventType:** `hand-complete`

---

### User Events

User events are targeted to a specific player. They implement `UserEvent`.

#### UserMessage

A direct message sent to a specific player (e.g., validation errors, notifications).

| Field       | Type            | Description             |
|-------------|-----------------|-------------------------|
| `timestamp` | Instant         | When the message was sent |
| `userId`    | String          | Target player ID        |
| `severity`  | MessageSeverity | INFO, WARNING, ERROR    |
| `message`   | String          | Message content         |

**eventType:** `user-message`

---

### System Events

#### SystemError

Internal server error. Not normally sent to clients; used for logging/monitoring.

| Field       | Type             | Description                  |
|-------------|------------------|------------------------------|
| `timestamp` | Instant          | When the error occurred      |
| `gameId`    | String?          | Game ID (if applicable)      |
| `userId`    | String?          | User ID (if applicable)      |
| `tableId`   | String?          | Table ID (if applicable)     |
| `exception` | RuntimeException | The exception that occurred  |

**eventType:** `system-error`
