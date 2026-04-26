# Command & Event Specification

This document catalogs all commands and events in the poker server. It serves as the contract between server and client.

## Conventions

- **Commands** are submitted by clients (via WebSocket or REST) to request state changes.
- **Events** are emitted by the server to notify clients of state changes.
- All commands use a `commandId` JSON discriminator derived from the class name in kebab-case (e.g., `JoinGame` -> `join-game`).
- All events use an `eventType` JSON discriminator derived from the class name in kebab-case (e.g., `HandStarted` -> `hand-started`).
- The `user` field on commands is **never serialized** — it is injected server-side from the authenticated session.
- **Seat positions are 1-indexed.** Every `seatPosition`, `dealerPosition`, `actionPosition`, `smallBlindPosition`, `bigBlindPosition`, `lastRaiserPosition`, and `Pot.seatPositions` value on the wire is in the range `1..numberOfSeats` (i.e. seat 1 through seat 9 for a 9-seat table). Clients should render these values as-is.
- **All `GameEvent` payloads carry a `sequenceNumber` field** (a `long` immediately after `timestamp`). It is the per-stream monotonic ID used for client-side gap detection. See [Sequence Numbers & Gap Detection](#sequence-numbers--gap-detection) below for the full contract.

---

## Sequence Numbers & Gap Detection

The server maintains two independent monotonic streams per game:

- **Game stream** — one counter at the game level. Stamps every broadcast `GameEvent` that is NOT a `TableEvent` (e.g. `GameStatusChanged`, `PlayerJoined`, `PlayerDisconnected`).
- **Table stream(s)** — one counter per table. Stamps every broadcast `TableEvent` for that table (e.g. `HandStarted`, `BlindPosted`, `PlayerActed`).

Both counters start at `1` and advance by `1` per stamped event. They are independent — table-1 seq 5 and table-2 seq 5 are unrelated. Counters are in-memory only; a server restart resets them, and clients recover by requesting a fresh snapshot.

### What carries a sequence number

| Event category | Stamped? | Notes |
|----------------|----------|-------|
| Broadcast `GameEvent` (not `TableEvent`) | Yes | Stamped from the game stream. |
| Broadcast `TableEvent` | Yes | Stamped from that table's stream. |
| `UserEvent` (incl. `HoleCardsDealt`, snapshots, `UserMessage`) | **No** | Always `sequenceNumber = 0`. Excluded from gap detection. |
| `SystemError` | No | Per-user-targeted; carries no `sequenceNumber` field. |

`HoleCardsDealt` implements both `TableEvent` and `UserEvent`. The `UserEvent` filter takes precedence — it is delivered only to the target player and carries `sequenceNumber = 0`. **Clients must not advance the table-stream expectation when receiving a `HoleCardsDealt`.**

### Client recovery flow

A client tracks one expected counter per stream it is subscribed to (one for the game stream plus one per table it is watching).

For each received broadcast event:

| `event.sequenceNumber` vs expected | Action |
|---|---|
| `seq == expected` | Accept; increment expected. |
| `seq > expected` | Gap detected; discard local state for this stream and request a snapshot via `GetGameState` / `GetTableState`. |
| `seq < expected` | Duplicate or out-of-order; ignore. |

After a snapshot, resume from the `gameStreamSeq` / `tableStreamSeqs` (on `GameSnapshot`) or `streamSeq` (on `TableSnapshot`) carried in the snapshot payload. The next broadcast event on that stream will be `seq + 1`.

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

| Field            | Type       | Description                  |
|------------------|------------|------------------------------|
| `timestamp`      | Instant    | When the transition occurred |
| `sequenceNumber` | long       | Game-stream sequence number  |
| `gameId`         | String     | Game ID                      |
| `oldStatus`      | GameStatus | Previous state               |
| `newStatus`      | GameStatus | New state                    |

**eventType:** `game-status-changed`

**GameStatus values:** `SCHEDULED`, `SEATING`, `ACTIVE`, `PAUSED`, `COMPLETED`

---

#### GameMessage

Informational message broadcast to all game participants.

| Field            | Type    | Description                 |
|------------------|---------|-----------------------------|
| `timestamp`      | Instant | When emitted                |
| `sequenceNumber` | long    | Game-stream sequence number |
| `gameId`         | String  | Game ID                     |
| `message`        | String  | Message content             |

**eventType:** `game-message`

---

#### PlayerBuyIn

A player bought chips.

| Field            | Type    | Description                 |
|------------------|---------|-----------------------------|
| `timestamp`      | Instant | When the buy-in occurred    |
| `sequenceNumber` | long    | Game-stream sequence number |
| `gameId`         | String  | Game ID                     |
| `userId`         | String  | Player who bought in        |
| `amount`         | int     | Chips purchased             |
| `newChipCount`   | int     | Player's total chip count   |

**eventType:** `player-buy-in`

---

#### PlayerJoined

A player has joined the game.

| Field            | Type    | Description                 |
|------------------|---------|-----------------------------|
| `timestamp`      | Instant | When the player joined      |
| `sequenceNumber` | long    | Game-stream sequence number |
| `gameId`         | String  | Game ID                     |
| `userId`         | String  | Player who joined           |

**eventType:** `player-joined`

---

#### PlayerSeated

A player was assigned to a seat at a table. Emitted during initial seating (SCHEDULED -> SEATING transition), when a player joins during SEATING/ACTIVE, or when a player rejoins after leaving.

| Field            | Type    | Description                      |
|------------------|---------|----------------------------------|
| `timestamp`      | Instant | When the player was seated       |
| `sequenceNumber` | long    | Game-stream sequence number      |
| `gameId`         | String  | Game ID                          |
| `userId`         | String  | Player who was seated            |
| `tableId`        | String  | Table the player was assigned to |

**eventType:** `player-seated`

---

#### PlayerMovedTables

A player was reassigned to a different table during table balancing. Only emitted when the player's table actually changes.

| Field            | Type    | Description                 |
|------------------|---------|-----------------------------|
| `timestamp`      | Instant | When the move occurred      |
| `sequenceNumber` | long    | Game-stream sequence number |
| `gameId`         | String  | Game ID                     |
| `userId`         | String  | Player who was moved        |
| `fromTableId`    | String  | Previous table              |
| `toTableId`      | String  | Destination table           |

**eventType:** `player-moved-tables`

---

#### PlayerDisconnected

A player's last active WebSocket listener for this game went away. Other players can use this to render presence state ("Alice disconnected") without confusing it with a `LeaveGame` (the player remains in the game).

Emitted on the 1→0 transition of the per-user listener ref count. If the same user has multiple sockets connected, this fires only when the **last** socket closes. The player's seat, chips, and game state are unaffected.

| Field            | Type    | Description                              |
|------------------|---------|------------------------------------------|
| `timestamp`      | Instant | When the last listener for the user left |
| `sequenceNumber` | long    | Game-stream sequence number              |
| `gameId`         | String  | Game ID                                  |
| `userId`         | String  | Player whose connection dropped          |

**eventType:** `player-disconnected`

**Notes:**
- The existing action-timeout path (`PlayerTimedOut`) continues to be the only thing that acts on an absent player's turn. A disconnect alone does not auto-fold or auto-leave.
- Clients may delay surfacing this in the UI to debounce flaky connections; the event itself fires immediately.

---

#### PlayerReconnected

A player's WebSocket listener came back after a previous `PlayerDisconnected`. Emitted on the 0→1 transition of the per-user listener ref count, **only when a `Player` record already exists for that user in the game**. If no `Player` record exists yet, the existing `PlayerJoined` / `JoinGame` flow handles the announcement instead.

| Field            | Type    | Description                                                |
|------------------|---------|------------------------------------------------------------|
| `timestamp`      | Instant | When the first new listener for the user attached          |
| `sequenceNumber` | long    | Game-stream sequence number                                |
| `gameId`         | String  | Game ID                                                    |
| `userId`         | String  | Player whose connection was restored                       |

**eventType:** `player-reconnected`

---

#### AdminViewingReplay

Broadcast warning that an admin is viewing a replay of a hand on a game whose status is not `COMPLETED`. Emitted exactly once per replay request against an in-progress game; not emitted for replays of `COMPLETED` games. Players receiving this can surface it in the UI to make admin observation visible.

| Field            | Type    | Description                                       |
|------------------|---------|---------------------------------------------------|
| `timestamp`      | Instant | When the warning was emitted                      |
| `sequenceNumber` | long    | Game-stream sequence number                       |
| `gameId`         | String  | Game ID                                           |
| `adminUserId`    | String  | Admin's user ID                                   |
| `adminAlias`     | String  | Admin's alias (display name)                      |
| `tableId`        | String  | Table being replayed                              |
| `handNumber`     | int     | Hand number being replayed                        |

**eventType:** `admin-viewing-replay`

**Why `adminAlias` is in-band:** unlike most player-scoped events, the admin viewing a replay may not have a `Player` record on the game (they are observing, not playing). Without a `Player` entry, clients cannot resolve the admin's display name from the game snapshot. Carrying the alias on the event lets the client render `'<alias> is reviewing hand N'` directly without a separate user lookup.

---

### Table-Level Events

Table-level events report changes within a specific table's hand. They implement `TableEvent`.

#### TableStatusChanged

The table's administrative status transitioned.

| Field            | Type         | Description                                          |
|------------------|--------------|------------------------------------------------------|
| `timestamp`      | Instant      | When the transition occurred                         |
| `sequenceNumber` | long         | Per-table stream sequence number                     |
| `gameId`         | String       | Game ID                                              |
| `tableId`        | String       | Table ID                                             |
| `oldStatus`      | Table.Status | Previous status                                      |
| `newStatus`      | Table.Status | New status (`PLAYING`, `PAUSE_AFTER_HAND`, `PAUSED`) |

**eventType:** `table-status-changed`

---

#### HandPhaseChanged

The table's hand phase transitioned. Emitted on every phase change (including transient phases like DEAL/FLOP/TURN/RIVER/SHOWDOWN/HAND_COMPLETE), so clients can drive UI state directly from this event.

| Field            | Type      | Description                      |
|------------------|-----------|----------------------------------|
| `timestamp`      | Instant   | When the transition occurred     |
| `sequenceNumber` | long      | Per-table stream sequence number |
| `gameId`         | String    | Game ID                          |
| `tableId`        | String    | Table ID                         |
| `oldPhase`       | HandPhase | Previous hand phase              |
| `newPhase`       | HandPhase | New hand phase                   |

**eventType:** `hand-phase-changed`

---

#### WaitingForPlayers

The table is in `WAITING_FOR_PLAYERS` because there are not enough eligible players to start a hand.

| Field            | Type    | Description                                              |
|------------------|---------|----------------------------------------------------------|
| `timestamp`      | Instant | When emitted                                             |
| `sequenceNumber` | long    | Per-table stream sequence number                         |
| `gameId`         | String  | Game ID                                                  |
| `tableId`        | String  | Table ID                                                 |
| `activePlayers`  | int     | Number of players currently eligible to play (with chips)|
| `seatedPlayers`  | int     | Number of non-empty seats at the table                   |

**eventType:** `waiting-for-players`

---

#### HandStarted

A new hand has begun. Carries a full seat snapshot so clients can render the table without additional state lookups.

| Field                | Type               | Description                                                 |
|----------------------|--------------------|-------------------------------------------------------------|
| `timestamp`          | Instant            | When the hand started                                       |
| `sequenceNumber`     | long               | Per-table stream sequence number                            |
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

**Emission order around blinds:** `BlindPosted(SMALL)` → `BlindPosted(BIG)` → `HandStarted` → `HoleCardsDealt` (per player, user-targeted) → `HandPhaseChanged(PRE_FLOP_BETTING)` → `ActionOnPlayer`. The `seats[]` snapshot in `HandStarted` reflects post-blind chip counts; the two `BlindPosted` events are the authoritative transaction record for the chips that moved.

---

#### BlindPosted

A blind has been posted. Emitted twice per hand under normal play — once for the small blind, once for the big blind, in that order. The `amountPosted` is the actual chips deducted from the player; for an all-in-on-blind the amount equals the player's stack and may be less than the configured blind level.

| Field            | Type      | Description                                                                |
|------------------|-----------|----------------------------------------------------------------------------|
| `timestamp`      | Instant   | When the blind was posted                                                  |
| `sequenceNumber` | long      | Per-table stream sequence number                                           |
| `gameId`         | String    | Game ID                                                                    |
| `tableId`        | String    | Table ID                                                                   |
| `seatPosition`   | int       | 1-indexed seat position of the player posting the blind                    |
| `userId`         | String    | Player posting the blind                                                   |
| `blindType`      | BlindType | `SMALL` or `BIG` (extensible — see [BlindType](#blindtype))                |
| `amountPosted`   | long      | Actual chips deducted (`min(blindLevel, playerStack)`; less than the blind level indicates all-in-on-blind) |

**eventType:** `blind-posted`

**All-in-on-blind detection:** if `amountPosted < expected blind level for this seat`, the player went all-in posting the blind. The seat's `isAllIn` flag in subsequent `HandStarted.seats[]` / `BettingRoundComplete.seats[]` entries will reflect this. No separate event is emitted for this case.

---

#### HoleCardsDealt

Hole cards dealt to a specific player. This is a **private event** — only the target player should see the card values.

| Field            | Type           | Description                                                        |
|------------------|----------------|--------------------------------------------------------------------|
| `timestamp`      | Instant        | When cards were dealt                                              |
| `sequenceNumber` | long           | Always `0` — `HoleCardsDealt` is a `UserEvent` and is excluded from gap detection. Clients must not advance the table-stream expectation when receiving this event. |
| `gameId`         | String         | Game ID                                                            |
| `tableId`        | String         | Table ID                                                           |
| `userId`         | String         | Player receiving cards                                             |
| `seatPosition`   | int            | 1-indexed seat position                                            |
| `cards`          | List<SeatCard> | The player's own dealt cards                                       |
| `seatsWithCards` | List<Integer>  | 1-indexed positions of all seats that received cards (for rendering face-down indicators) |

**eventType:** `hole-cards-dealt`
**Implements:** `TableEvent`, `UserEvent` (dual interface for privacy filtering)

---

#### CommunityCardsDealt

Community cards dealt (flop, turn, or river).

| Field                | Type       | Description                                           |
|----------------------|------------|-------------------------------------------------------|
| `timestamp`          | Instant    | When cards were dealt                                 |
| `sequenceNumber`     | long       | Per-table stream sequence number                      |
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
| `sequenceNumber`   | long             | Per-table stream sequence number                                |
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

| Field            | Type         | Description                           |
|------------------|--------------|---------------------------------------|
| `timestamp`      | Instant      | When the timeout occurred             |
| `sequenceNumber` | long         | Per-table stream sequence number      |
| `gameId`         | String       | Game ID                               |
| `tableId`        | String       | Table ID                              |
| `seatPosition`   | int          | 1-indexed seat position of the player |
| `userId`         | String       | Player who timed out                  |
| `defaultAction`  | PlayerAction | The action applied (Check/Fold)       |

**eventType:** `player-timed-out`
**Follow-up:** A `PlayerActed` event for the same seat is emitted immediately afterward carrying the post-action state (resulting status, pot total, etc.).

---

#### ActionOnPlayer

Action has moved to a specific seat. Carries the full decision context so clients can render the action UI without additional state lookups.

| Field              | Type    | Description                                                       |
|--------------------|---------|-------------------------------------------------------------------|
| `timestamp`        | Instant | When the action moved                                             |
| `sequenceNumber`   | long    | Per-table stream sequence number                                  |
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
| `sequenceNumber` | long               | Per-table stream sequence number                  |
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

| Field            | Type             | Description                      |
|------------------|------------------|----------------------------------|
| `timestamp`      | Instant          | When showdown resolved           |
| `sequenceNumber` | long             | Per-table stream sequence number |
| `gameId`         | String           | Game ID                          |
| `tableId`        | String           | Table ID                         |
| `potResults`     | List<PotResult>  | Results for each pot             |

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

| Field            | Type    | Description                      |
|------------------|---------|----------------------------------|
| `timestamp`      | Instant | When the hand completed          |
| `sequenceNumber` | long    | Per-table stream sequence number |
| `gameId`         | String  | Game ID                          |
| `tableId`        | String  | Table ID                         |
| `handNumber`     | int     | The completed hand number        |

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

A snapshot of the current game state, sent in response to a `GetGameState` command. Carries resume-point fields the client uses to re-anchor gap detection on every stream after a snapshot-based recovery.

| Field              | Type                | Description                                                                    |
|--------------------|---------------------|--------------------------------------------------------------------------------|
| `timestamp`        | Instant             | When the snapshot was taken                                                    |
| `userId`           | String              | Requesting player ID                                                           |
| `gameId`           | String              | Game ID                                                                        |
| `gameName`         | String              | Human-readable game name                                                       |
| `status`           | GameStatus          | Current game status                                                            |
| `startTime`        | Instant             | Scheduled start time                                                           |
| `smallBlind`       | int                 | Small blind amount                                                             |
| `bigBlind`         | int                 | Big blind amount                                                               |
| `players`          | List\<Player>       | All players in the game                                                        |
| `tableIds`         | List\<String>       | IDs of all tables in the game                                                  |
| `gameStreamSeq`    | long                | Most recently assigned game-stream sequence number. Resume from this value: the next broadcast `GameEvent` (non-`TableEvent`) will be `gameStreamSeq + 1`. |
| `tableStreamSeqs`  | Map<String, long>   | `tableId → most recently assigned per-table sequence number`. The next broadcast `TableEvent` for a given table will be `tableStreamSeqs[tableId] + 1`. |

**eventType:** `game-snapshot`

`GameSnapshot` is itself a `UserEvent` and is delivered only to the requesting user. It carries no `sequenceNumber` of its own.

---

#### TableSnapshot

A snapshot of a specific table's state, sent in response to a `GetTableState` command. Hole cards and pending intents are stripped from all seats except the requesting user's. Carries the resume-point for this table's stream.

| Field       | Type    | Description                                                                                       |
|-------------|---------|---------------------------------------------------------------------------------------------------|
| `timestamp` | Instant | When the snapshot was taken                                                                       |
| `userId`    | String  | Requesting player ID                                                                              |
| `gameId`    | String  | Game ID                                                                                           |
| `table`     | Table   | Full table state (sanitized)                                                                      |
| `streamSeq` | long    | Most recently assigned per-table sequence number. The next broadcast `TableEvent` for this table will be `streamSeq + 1`. |

**eventType:** `table-snapshot`

`TableSnapshot` is itself a `UserEvent` and is delivered only to the requesting user. It carries no `sequenceNumber` of its own — `streamSeq` is the resume point for the table stream, distinct from the snapshot's identity.

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

### BlindType

The kind of blind being posted in a `BlindPosted` event. Additively extensible: the wire format is the enum name, so adding `ANTE`, `STRADDLE`, or `DEAD_BLIND` later is forward-compatible.

| Value   | Description                                  |
|---------|----------------------------------------------|
| `SMALL` | Small blind (posted by the SB seat)          |
| `BIG`   | Big blind (posted by the BB seat)            |

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
