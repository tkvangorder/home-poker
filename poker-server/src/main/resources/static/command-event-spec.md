# Command & Event Specification

This document catalogs all commands and events in the poker server. It serves as the contract between server and client.

## Conventions

- **Commands** are submitted by clients (via WebSocket or REST) to request state changes.
- **Events** are emitted by the server to notify clients of state changes.
- All commands use a `commandId` JSON discriminator derived from the class name in kebab-case (e.g., `JoinGame` -> `join-game`).
- All events use an `eventType` JSON discriminator derived from the class name in kebab-case (e.g., `HandStarted` -> `hand-started`).
- The `user` field on commands is **never serialized** — it is injected server-side from the authenticated session.
- **Seat positions are 1-indexed.** Every `seatPosition`, `dealerPosition`, `actionPosition`, `smallBlindPosition`, `bigBlindPosition`, `lastRaiserPosition`, and `Pot.seatPositions` value on the wire is in the range `1..numberOfSeats` (i.e. seat 1 through seat 9 for a 9-seat table). Clients should render these values as-is.

---

## WebSocket Connection

### Endpoint

```
ws://<host>/ws/games/{gameId}?token=<jwt>
```

### Authentication

The browser WebSocket API does not support custom headers, so the JWT token is passed as a **query parameter** during the initial connection handshake.

1. **Obtain a JWT token** — Authenticate via the REST API (`POST /api/auth/login`) to receive a JWT token.
2. **Connect with the token** — Open a WebSocket connection with the token as a query parameter:
   ```
   ws://localhost:8080/ws/games/abc123?token=eyJhbGciOiJIUzI1NiJ9...
   ```
3. **Handshake validation** — The server intercepts the handshake and:
   - Extracts the `token` query parameter
   - Validates the JWT and resolves the authenticated user
   - Extracts the `gameId` from the URL path
   - Rejects the connection (HTTP 403) if the token is missing, invalid, or expired

Once connected, the authenticated user identity is stored in the WebSocket session and automatically injected into all subsequent commands — clients never need to include user information in command payloads.

### JavaScript Example

```javascript
// 1. Authenticate via REST to get a JWT token
const response = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ loginId: 'player1', password: 'secret' })
});
const { token } = await response.json();

// 2. Open WebSocket with token as query parameter
const gameId = 'abc123';
const ws = new WebSocket(`ws://localhost:8080/ws/games/${gameId}?token=${token}`);

// 3. Listen for events from the server
ws.onmessage = (event) => {
  const pokerEvent = JSON.parse(event.data);
  console.log(pokerEvent.eventType, pokerEvent);
};

// 4. Send commands (no user field needed — server injects it)
ws.send(JSON.stringify({
  commandId: 'join-game',
  gameId: gameId
}));
```

### Connection Lifecycle

| Phase | Description |
|-------|-------------|
| **Handshake** | JWT validated, user resolved, gameId extracted. Connection rejected if any step fails. |
| **Connected** | Client receives game events and sends commands as JSON text messages. |
| **Disconnected** | Server cleans up the game listener. The player remains in the game but stops receiving events. |

---

## Commands

### Game-Level Commands

Game-level commands affect game-wide state (registration, lifecycle, chips). They implement `GameCommand` and are routed through `GameManager.applyCommand()`.

#### JoinGame

Adds a player to the game. If the game is in SEATING or ACTIVE state, the player is immediately assigned to a seat on the table with the fewest players. If the player previously left the game (status OUT), they are allowed to rejoin. Triggered when a user connects via WebSocket.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `join-game`
**Accepted in states:** SCHEDULED, SEATING, ACTIVE, PAUSED
**Emits:** `PlayerJoined`, and `PlayerSeated` if assigned to a table

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

Player leaves the game. If a hand is in progress, the leave takes effect after the current hand. The player record is kept in the game with status OUT for auditing (buy-in history, chip counts). A player who has left may rejoin later via `JoinGame`. Triggered when a user disconnects from WebSocket or sends this command explicitly.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `leave-game`
**Accepted in states:** SCHEDULED, SEATING, ACTIVE, PAUSED
**Emits:** `GameMessage`

---

#### GetGameState

Requests a snapshot of the current game state. The server responds with a `GameSnapshot` user event sent only to the requesting user.

| Field    | Type   | Description       |
|----------|--------|-------------------|
| `gameId` | String | Target game ID    |
| `user`   | User   | (server-injected) |

**commandId:** `get-game-state`
**Accepted in states:** Any

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

#### GetTableState

Requests a snapshot of a specific table's state. The server responds with a `TableSnapshot` user event sent only to the requesting user. Hole cards and pending intents are stripped from all seats except the requesting user's.

| Field     | Type   | Description       |
|-----------|--------|-------------------|
| `gameId`  | String | Target game ID    |
| `tableId` | String | Target table ID   |
| `user`    | User   | (server-injected) |

**commandId:** `get-table-state`
**Accepted in phases:** Any

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

#### PlayerJoined

A player has joined the game.

| Field       | Type    | Description              |
|-------------|---------|--------------------------|
| `timestamp` | Instant | When the player joined   |
| `gameId`    | String  | Game ID                  |
| `userId`    | String  | Player who joined        |

**eventType:** `player-joined`

---

#### PlayerSeated

A player was assigned to a seat at a table. Emitted during initial seating (SCHEDULED -> SEATING transition), when a player joins during SEATING/ACTIVE, or when a player rejoins after leaving.

| Field       | Type    | Description                      |
|-------------|---------|----------------------------------|
| `timestamp` | Instant | When the player was seated       |
| `gameId`    | String  | Game ID                          |
| `userId`    | String  | Player who was seated            |
| `tableId`   | String  | Table the player was assigned to |

**eventType:** `player-seated`

---

#### PlayerMovedTables

A player was reassigned to a different table during table balancing. Only emitted when the player's table actually changes.

| Field         | Type    | Description                  |
|---------------|---------|------------------------------|
| `timestamp`   | Instant | When the move occurred       |
| `gameId`      | String  | Game ID                      |
| `userId`      | String  | Player who was moved         |
| `fromTableId` | String  | Previous table               |
| `toTableId`   | String  | Destination table            |

**eventType:** `player-moved-tables`

---

### Table-Level Events

Table-level events report changes within a specific table's hand. They implement `TableEvent`.

#### TableStatusChanged

The table's administrative status transitioned.

| Field       | Type         | Description                                          |
|-------------|--------------|------------------------------------------------------|
| `timestamp` | Instant      | When the transition occurred                         |
| `gameId`    | String       | Game ID                                              |
| `tableId`   | String       | Table ID                                             |
| `oldStatus` | Table.Status | Previous status                                      |
| `newStatus` | Table.Status | New status (`PLAYING`, `PAUSE_AFTER_HAND`, `PAUSED`) |

**eventType:** `table-status-changed`

---

#### HandPhaseChanged

The table's hand phase transitioned. Emitted on every phase change (including transient phases like DEAL/FLOP/TURN/RIVER/SHOWDOWN/HAND_COMPLETE), so clients can drive UI state directly from this event.

| Field       | Type      | Description                  |
|-------------|-----------|------------------------------|
| `timestamp` | Instant   | When the transition occurred |
| `gameId`    | String    | Game ID                      |
| `tableId`   | String    | Table ID                     |
| `oldPhase`  | HandPhase | Previous hand phase          |
| `newPhase`  | HandPhase | New hand phase               |

**eventType:** `hand-phase-changed`

---

#### WaitingForPlayers

The table is in `WAITING_FOR_PLAYERS` because there are not enough eligible players to start a hand.

| Field           | Type    | Description                                              |
|-----------------|---------|----------------------------------------------------------|
| `timestamp`     | Instant | When emitted                                             |
| `gameId`        | String  | Game ID                                                  |
| `tableId`       | String  | Table ID                                                 |
| `activePlayers` | int     | Number of players currently eligible to play (with chips)|
| `seatedPlayers` | int     | Number of non-empty seats at the table                   |

**eventType:** `waiting-for-players`

---

#### HandStarted

A new hand has begun. Carries a full seat snapshot so clients can render the table without additional state lookups.

| Field                | Type               | Description                                                 |
|----------------------|--------------------|-------------------------------------------------------------|
| `timestamp`          | Instant            | When the hand started                                       |
| `gameId`             | String             | Game ID                                                     |
| `tableId`            | String             | Table ID                                                    |
| `handNumber`         | int                | Monotonically increasing                                    |
| `dealerPosition`     | int                | 1-indexed seat position of the dealer button                |
| `smallBlindPosition` | int                | 1-indexed seat position of the small blind                  |
| `bigBlindPosition`   | int                | 1-indexed seat position of the big blind                    |
| `smallBlindAmount`   | int                | Small blind chip amount                                     |
| `bigBlindAmount`     | int                | Big blind chip amount                                       |
| `currentBet`         | int                | Amount to call at the start of PRE_FLOP_BETTING (= BB)      |
| `minimumRaise`       | int                | Minimum raise amount (= BB)                                 |
| `seats`              | List<SeatSummary>  | Per-seat snapshot for every non-empty seat at the table     |

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
| `seatPosition` | int            | 1-indexed seat position    |
| `cards`        | List<SeatCard> | The dealt cards            |

**eventType:** `hole-cards-dealt`
**Implements:** `TableEvent`, `UserEvent` (dual interface for privacy filtering)

---

#### CommunityCardsDealt

Community cards dealt (flop, turn, or river).

| Field                | Type       | Description                                           |
|----------------------|------------|-------------------------------------------------------|
| `timestamp`          | Instant    | When cards were dealt                                 |
| `gameId`             | String     | Game ID                                               |
| `tableId`            | String     | Table ID                                              |
| `cards`              | List<Card> | The cards dealt in this event (3 for flop, 1 o/w)     |
| `phase`              | HandPhase  | Which transient phase dealt them: `FLOP`/`TURN`/`RIVER` |
| `allCommunityCards`  | List<Card> | The full board visible after this deal (3/4/5 cards)  |

**eventType:** `community-cards-dealt`

---

#### PlayerActed

A player performed an action during a betting round. The event carries the resulting per-hand status and the table's betting context *after* the action was applied, so clients can update their view directly.

| Field              | Type             | Description                                                     |
|--------------------|------------------|-----------------------------------------------------------------|
| `timestamp`        | Instant          | When the action occurred                                        |
| `gameId`           | String           | Game ID                                                         |
| `tableId`          | String           | Table ID                                                        |
| `seatPosition`     | int              | 1-indexed seat position of the player                           |
| `userId`           | String           | Player who acted                                                |
| `action`           | PlayerAction     | The action taken                                                |
| `chipCount`        | int              | Player's remaining chips                                        |
| `resultingStatus`  | HandPlayerStatus | Seat status after the action (`ACTIVE`, `FOLDED`, or `ALL_IN`)  |
| `currentBet`       | int              | Table's current bet after this action                           |
| `minimumRaise`     | int              | Minimum raise after this action                                 |
| `potTotal`         | int              | Sum of all pots after this action                               |

**eventType:** `player-acted`

---

#### PlayerTimedOut

A player failed to act within the time limit; a default action was applied.

| Field           | Type         | Description                       |
|-----------------|--------------|-----------------------------------|
| `timestamp`     | Instant      | When the timeout occurred         |
| `gameId`        | String       | Game ID                           |
| `tableId`       | String       | Table ID                          |
| `seatPosition`  | int          | 1-indexed seat position of the player |
| `userId`        | String       | Player who timed out              |
| `defaultAction` | PlayerAction | The action applied (Check/Fold)   |

**eventType:** `player-timed-out`
**Follow-up:** A `PlayerActed` event for the same seat is emitted immediately afterward carrying the post-action state (resulting status, pot total, etc.).

---

#### ActionOnPlayer

Action has moved to a specific seat. Carries the full decision context so clients can render the action UI without additional state lookups.

| Field              | Type    | Description                                                       |
|--------------------|---------|-------------------------------------------------------------------|
| `timestamp`        | Instant | When the action moved                                             |
| `gameId`           | String  | Game ID                                                           |
| `tableId`          | String  | Table ID                                                          |
| `seatPosition`     | int     | 1-indexed seat position of the player on the clock                |
| `userId`           | String  | The player on the clock                                           |
| `actionDeadline`   | Instant | When the player must act by                                       |
| `currentBet`       | int     | Amount to call (the table's current bet)                          |
| `minimumRaise`     | int     | Minimum raise amount                                              |
| `callAmount`       | int     | Chips this seat owes to call (`currentBet - seat.currentBetAmount`) |
| `playerChipCount`  | int     | This player's chip count                                          |
| `potTotal`         | int     | Sum of chips across all pots                                      |

**eventType:** `action-on-player`

---

#### BettingRoundComplete

All active players have acted; the betting round is finished. Includes a full per-seat snapshot so clients know who folded / went all-in during the round.

| Field            | Type               | Description                                       |
|------------------|--------------------|---------------------------------------------------|
| `timestamp`      | Instant            | When the round completed                          |
| `gameId`         | String             | Game ID                                           |
| `tableId`        | String             | Table ID                                          |
| `completedPhase` | HandPhase          | Which betting phase completed                     |
| `pots`           | List<Table.Pot>    | Pot state after collection (side pots separated)  |
| `seats`          | List<SeatSummary>  | Per-seat snapshot for every non-empty seat        |
| `potTotal`       | int                | Sum of chips across all pots                      |

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
| `seatPosition`    | int    | Winning player's 1-indexed seat position |
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

#### GameSnapshot

A snapshot of the current game state, sent in response to a `GetGameState` command.

| Field       | Type          | Description                     |
|-------------|---------------|---------------------------------|
| `timestamp` | Instant       | When the snapshot was taken      |
| `userId`    | String        | Requesting player ID             |
| `gameId`    | String        | Game ID                          |
| `gameName`  | String        | Human-readable game name         |
| `status`    | GameStatus    | Current game status              |
| `startTime` | Instant       | Scheduled start time             |
| `smallBlind`| int           | Small blind amount               |
| `bigBlind`  | int           | Big blind amount                 |
| `players`   | List\<Player> | All players in the game          |
| `tableIds`  | List\<String> | IDs of all tables in the game    |

**eventType:** `game-snapshot`

---

#### TableSnapshot

A snapshot of a specific table's state, sent in response to a `GetTableState` command. Hole cards and pending intents are stripped from all seats except the requesting user's.

| Field       | Type    | Description                    |
|-------------|---------|--------------------------------|
| `timestamp` | Instant | When the snapshot was taken     |
| `userId`    | String  | Requesting player ID           |
| `gameId`    | String  | Game ID                        |
| `table`     | Table   | Full table state (sanitized)   |

**eventType:** `table-snapshot`

---

---

## Model Reference

### Table

The `Table` object represents the full state of a poker table. It is nested within the `TableSnapshot` event.

| Field                | Type         | Nullable | Description                               |
|----------------------|--------------|----------|-------------------------------------------|
| `id`                 | String       | No       | Unique table identifier                   |
| `seats`              | List<Seat>   | No       | Ordered list of seats at the table        |
| `status`             | Table.Status | No       | Current table status                      |
| `handPhase`          | HandPhase    | No       | Current phase of the hand in progress     |
| `dealerPosition`     | Integer      | Yes      | 1-indexed seat position of the dealer button |
| `actionPosition`     | Integer      | Yes      | 1-indexed seat position of the player on the clock |
| `smallBlindPosition` | Integer      | Yes      | 1-indexed seat position of the small blind |
| `bigBlindPosition`   | Integer      | Yes      | 1-indexed seat position of the big blind   |
| `lastRaiserPosition` | Integer      | Yes      | 1-indexed seat position of the last raiser |
| `currentBet`         | int          | No       | The current bet amount to be matched      |
| `minimumRaise`       | int          | No       | The minimum raise amount                  |
| `handNumber`         | int          | No       | Monotonically increasing hand counter     |
| `phaseStartedAt`     | Instant      | Yes      | When the current phase began              |
| `actionDeadline`     | Instant      | Yes      | Deadline for the current player to act    |
| `communityCards`     | List<Card>   | No       | Community cards dealt so far              |
| `pots`               | List<Pot>    | No       | Current pot(s) in play                    |

The `seats` list is ordered: index `i` in the list corresponds to **seat position `i + 1`** (1-indexed). All `*Position` fields above, and every `seatPosition` in events, refer to this 1-indexed numbering. Implementations that need to look up a seat from a position should use `seats[position - 1]`.

**Table.Status values:** `PLAYING`, `PAUSE_AFTER_HAND`, `PAUSED`

**HandPhase values:** `WAITING_FOR_PLAYERS`, `PREDEAL`, `DEAL`, `PRE_FLOP_BETTING`, `FLOP`, `FLOP_BETTING`, `TURN`, `TURN_BETTING`, `RIVER`, `RIVER_BETTING`, `SHOWDOWN`, `HAND_COMPLETE`

---

### Seat

Each entry in the table's `seats` list represents one seat position.

| Field              | Type           | Nullable | Description                                           |
|--------------------|----------------|----------|-------------------------------------------------------|
| `status`           | Seat.Status    | No       | Current seat status                                   |
| `player`           | Player         | Yes      | The player occupying this seat (null if empty)        |
| `cards`            | List<SeatCard> | Yes      | Player's hole cards (stripped for other players)      |
| `action`           | PlayerAction   | Yes      | The player's last action in the current betting round |
| `currentBetAmount` | int            | No       | Chips the player has bet in the current round         |
| `isAllIn`          | boolean        | No       | Whether the player is all-in                          |
| `mustPostBlind`    | boolean        | No       | Whether the player must post a blind to enter play    |
| `missedBigBlind`   | boolean        | No       | Whether the player has missed a big blind rotation    |
| `pendingIntent`    | PlayerAction   | Yes      | Pre-selected action (stripped for other players)      |

**Seat.Status values:** `ACTIVE`, `FOLDED`, `JOINED_WAITING`, `EMPTY`

---

### SeatCard

A card dealt to a player at the table.

| Field      | Type    | Description                                          |
|------------|---------|------------------------------------------------------|
| `card`     | Card    | The card value and suit                              |
| `showCard` | boolean | Whether the card should be revealed after the hand   |

---

### Card

| Field   | Type      | Description                                       |
|---------|-----------|---------------------------------------------------|
| `value` | CardValue | Card rank: `TWO` through `ACE`                    |
| `suit`  | CardSuit  | Card suit: `CLUBS`, `DIAMONDS`, `HEARTS`, `SPADES`|

---

### Pot

| Field           | Type          | Description                                  |
|-----------------|---------------|----------------------------------------------|
| `amount`        | int           | Total chips in the pot                       |
| `seatPositions` | List<Integer> | 1-indexed seat positions eligible to win this pot |

---

### SeatSummary

Compact per-seat snapshot embedded in table events (`HandStarted`, `BettingRoundComplete`). Captures the minimum state a client needs to render each seat at a point in time.

| Field              | Type             | Nullable | Description                                             |
|--------------------|------------------|----------|---------------------------------------------------------|
| `seatPosition`     | int              | No       | 1-indexed seat position (range 1..numberOfSeats)        |
| `userId`           | String           | Yes      | User ID of the seated player (null if empty)            |
| `status`           | HandPlayerStatus | No       | Codified per-hand status of the seat                    |
| `chipCount`        | int              | No       | Player's current chip count                             |
| `currentBetAmount` | int              | No       | Chips wagered by this seat in the current betting round |

---

### HandPlayerStatus

Codified status of a seat's player within the current hand. Distinct from `PlayerStatus` (game-session presence) and `Seat.Status` (coarser seat lifecycle state).

| Value         | Description                                                        |
|---------------|--------------------------------------------------------------------|
| `WAITING`     | Seated but joined mid-hand; not eligible this hand                 |
| `ACTIVE`      | In the hand, not currently on the clock                            |
| `TO_ACT`      | Action is currently on this seat                                   |
| `FOLDED`      | Folded this hand                                                   |
| `ALL_IN`      | Committed all chips; no further action this hand                   |
| `SITTING_OUT` | Seated but not participating (must post blind, missed BB, etc.)    |

---

### Player

| Field        | Type         | Nullable | Description                              |
|--------------|--------------|----------|------------------------------------------|
| `user`       | User         | No       | The user associated with this player     |
| `status`     | PlayerStatus | No       | Current player status                    |
| `chipCount`  | int          | No       | Player's current chip count              |
| `buyInTotal` | int          | No       | Total chips bought in                    |
| `reBuys`     | int          | No       | Number of re-buys                        |
| `addOns`     | int          | No       | Number of add-ons                        |
| `tableId`    | String       | Yes      | ID of the table the player is seated at  |

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
