# Cash Game State Management

## 1. Overview

The poker server uses a single-threaded game loop model where all game state mutations happen on one thread per game.
External inputs (player actions, admin commands) are submitted to a JCTools MPSC (Multi-Producer, Single-Consumer)
lock-free queue and drained each tick. The game loop fires approximately every 1 second in production
(`gameLoopIntervalMilliseconds: 1000`).

Each tick follows a fixed sequence:

1. **Drain commands** from the MPSC queue
2. **Apply commands** (validate + mutate state, queue events on error)
3. **Transition game** (game-level state machine)
4. **Transition tables** (table-level state machine, once per table)
5. **Persist** game state (throttled by `saveIntervalSeconds`)
6. **Publish events** to registered `GameListener` instances

There are two cooperating state machines:

- **Game-level** (`GameStatus`): Controls the lifecycle of the overall game session (scheduling, seating, active play,
  pause, completion).
- **Table-level** (`HandPhase`): Controls the lifecycle of a single poker hand at a table (deal, betting rounds,
  community cards, showdown).

The game-level machine can signal tables (e.g., "pause after this hand"), and tables report back to the game level
(e.g., "all tables have paused").

### Relevant Existing Classes

| Class | Module | Role |
|---|---|---|
| `GameManager<T>` | poker-server | Abstract game loop, command routing, game-level transitions |
| `CashGameManager` | poker-server | Cash game persistence adapter |
| `TableManager<T>` | poker-server | Abstract table-level command routing |
| `TexasHoldemTableManager<T>` | poker-server | Texas Hold'em table transitions (currently empty) |
| `CashGame` | poker-common | Cash game state model |
| `Table` | poker-common | Table state model |
| `Seat` | poker-common | Seat state model |
| `Player` | poker-common | Player state within a game |
| `GameSettings` | poker-server | Timing and sizing constants |
| `GameContext` | poker-server | Per-tick event accumulator and settings |
| `GameStateTransitions` | poker-server | Utility for seating resets |

---

## 2. Game-Level State Machine

### State Diagram

```
                              StartGame (admin)
                              + 2 players
                              + startTime reached
     SCHEDULED ──> SEATING ─────────────────────> ACTIVE
                                                    │  ▲
                                              Pause │  │ Resume
                                                    ▼  │
                                                  PAUSED
                                                    │
                         EndGame (admin)            │ EndGame (admin)
          ┌─────────────────────────────────────────┘
          │          EndGame from any state
          ▼
      COMPLETED
```

### Transition Table

| From | To | Guard                                                                                    | Actions |
|---|---|------------------------------------------------------------------------------------------|---|
| SCHEDULED | SEATING | `now >= startTime - seatingTimeSeconds`                                                  | Create tables via `GameStateTransitions.resetSeating()`. Distribute registered players to random seats with status `JOINED_WAITING`. |
| SEATING | ACTIVE | Admin issues optional `StartGame` AND `countOfSeatedPlayers >= 2` AND `now >= startTime` | Set all tables to `Table.Status.PLAYING`. Emit `GameMessage("Game is now active")`. |
| ACTIVE | PAUSED | Admin issues `PauseGame`                                                                 | Set all `PLAYING` tables to `Table.Status.PAUSE_AFTER_HAND`. When all tables report `PAUSED` status, set game status to `PAUSED`. (Two-phase pause, see below.) |
| PAUSED | ACTIVE | Admin issues `ResumeGame`                                                                | Set all tables to `Table.Status.PLAYING`. Emit `GameMessage("Game resumed")`. |
| ACTIVE | COMPLETED | Admin issues `EndGame`                                                                   | Set all tables to `PAUSE_AFTER_HAND`. When all tables are `PAUSED`, set game to `COMPLETED`. Record `endTime`. |
| PAUSED | COMPLETED | Admin issues `EndGame`                                                                   | Set game to `COMPLETED`. Record `endTime`. |
| SCHEDULED | COMPLETED | Admin issues `EndGame`                                                                   | Set game to `COMPLETED`. |
| SEATING | COMPLETED | Admin issues `EndGame`                                                                   | Set game to `COMPLETED`. |

### Two-Phase Pause

When an admin pauses or ends a game, hands in progress are not interrupted mid-play:

1. **Signal phase**: All `PLAYING` tables are set to `PAUSE_AFTER_HAND`.
2. **Completion phase**: Each table, upon reaching `HAND_COMPLETE`, checks its status. If `PAUSE_AFTER_HAND`, it
   transitions to `PAUSED` instead of starting a new hand.
3. **Game-level check**: During `transitionGame()`, if all tables are `PAUSED`, the game transitions to `PAUSED` (or
   `COMPLETED` for EndGame).

### Commands Accepted Per State

| Command | SCHEDULED | SEATING | ACTIVE | PAUSED | COMPLETED |
|---|---|---|---|---|---|
| RegisterForGame | Yes | Yes | Yes | Yes | No |
| UnregisterFromGame | Yes | No | No | No | No |
| StartGame (admin) | No | Yes | No | No | No |
| PauseGame (admin) | No | No | Yes | No | No |
| ResumeGame (admin) | No | No | No | Yes | No |
| EndGame (admin) | Yes | Yes | Yes | Yes | No |
| BuyIn | No | Yes | Yes | Yes | No |
| LeaveGame | No | Yes | Yes | Yes | No |
| Table commands | No | No | Yes | No | No |

### SEATING Phase Rules

- Tables are created and players are distributed to random seats during the `SCHEDULED -> SEATING` transition.
- New players who register during SEATING are assigned to a random empty seat on the table with the fewest players.
- The game remains in SEATING until an admin explicitly starts it (via `StartGame`) or (the game has been configured
  to auto-start once there are at least 2 player) and there are at least 2 seated players, and `startTime` has been
  reached.
- During SEATING, players can buy in so they have chips when play begins.

---

## 3. Table-Level State Machine (Texas Hold'em)

### HandPhase Enum

A new enum `HandPhase` replaces the coarse `Table.Status` for tracking where a hand is within its lifecycle. The
existing `Table.Status` (`PAUSED`, `PLAYING`, `PAUSE_AFTER_HAND`) remains as the table's administrative status and is
orthogonal to `HandPhase`.

```java
public enum HandPhase {
  WAITING_FOR_PLAYERS,  // No hand in progress, waiting for 2+ active players
  PREDEAL,              // Conditional: pause for buy-ins, rebuys, table moves, seating
  DEAL,                 // Transient: post blinds, deal hole cards
  PRE_FLOP_BETTING,     // Betting round (pre-flop)
  FLOP,                 // Transient: deal 3 community cards
  FLOP_BETTING,         // Betting round (flop)
  TURN,                 // Transient: deal 1 community card
  TURN_BETTING,         // Betting round (turn)
  RIVER,                // Transient: deal 1 community card
  RIVER_BETTING,        // Betting round (river)
  SHOWDOWN,             // Transient: evaluate hands, award pots
  HAND_COMPLETE         // Transient: cleanup, check for pause
}
```

**Transient phases** (`DEAL`, `FLOP`, `TURN`, `RIVER`, `SHOWDOWN`, `HAND_COMPLETE`) complete within a single tick. They
perform their action and immediately advance to the next phase. No player input is required.

**Waiting phases** (`PREDEAL`) persist across ticks but are conditionally entered. `PREDEAL` is only entered when there
is work to do (players needing to buy in, pending table moves, new arrivals to seat). It has a configurable timer and
advances to `DEAL` when all pending actions are resolved or the timer expires.

**Betting phases** (`PRE_FLOP_BETTING`, `FLOP_BETTING`, `TURN_BETTING`, `RIVER_BETTING`) persist across ticks. They
wait for the current player to act (or timeout). The phase advances only when the betting round is complete.

### State Diagram

```
  Table.Status == PLAYING && 2+ active players
            │
            ▼
  ┌─── WAITING_FOR_PLAYERS
  │         │  2+ active players ready
  │         ▼
  │    ┌─ PREDEAL (conditional) ──────────────────────────────┐
  │    │    │  all pending actions resolved or timer expires   │
  │    │    ▼                                                  │
  │    │  DEAL ──────────────────────────────────────────────┐ │
  │    │    │                                                │ │
  │    │    ▼                                                │ │
  │    │  PRE_FLOP_BETTING ──── all fold ──────────────┐     │ │
  │    │    │ betting complete                         │     │ │
  │    │    ▼                                          │     │ │
  │    │  FLOP                                         │     │ │
  │    │    │                                          │     │ │
  │    │    ▼                                          │     │ │
  │    │  FLOP_BETTING ──────── all fold ──────────┐   │     │ │
  │    │    │ betting complete                     │   │     │ │
  │    │    ▼                                      │   │     │ │
  │    │  TURN                                     │   │     │ │
  │    │    │                                      │   │     │ │
  │    │    ▼                                      │   │     │ │
  │    │  TURN_BETTING ──────── all fold ──────────┤   │     │ │
  │    │    │ betting complete                     │   │     │ │
  │    │    ▼                                      │   │     │ │
  │    │  RIVER                                    │   │     │ │
  │    │    │                                      │   │     │ │
  │    │    ▼                                      │   │     │ │
  │    │  RIVER_BETTING ─────── all fold ──────┐   │   │     │ │
  │    │    │ betting complete                 │   │   │     │ │
  │    │    ▼                                  │   │   │     │ │
  │    │  SHOWDOWN <──── (pots with 2+ players)│   │   │     │ │
  │    │    │                                  │   │   │     │ │
  │    │    ▼                                  ▼   ▼   ▼     │ │
  │    │  HAND_COMPLETE <──────────────────────────────┘     │ │
  │    │    │                                                │ │
  │    │    │ table status == PAUSE_AFTER_HAND?              │ │
  │    │    │──── yes ──> table status = PAUSED ─────────────┘ │
  │    │    │                                     (exit loop)  │
  │    │    │                                                  │
  │    │    │──── predeal needed? ──── yes ──> PREDEAL ────────┘
  │    │    │──── no + 2+ players ──> DEAL (skip PREDEAL)
  │    │    │──── no + <2 players ──> WAITING_FOR_PLAYERS
  │    │    │
  └────┘────┘
```

### Hand Phase Transition Table

| From | To | Guard | Actions |
|---|---|---|---|
| WAITING_FOR_PLAYERS | PREDEAL | `Table.status == PLAYING` AND `activeSeatCount >= 2` AND predeal needed (see Section 8) | Enter predeal processing. |
| WAITING_FOR_PLAYERS | DEAL | `Table.status == PLAYING` AND `activeSeatCount >= 2` AND no predeal needed | Skip directly to deal. |
| PREDEAL | DEAL | All pending predeal actions resolved OR predeal timer expires | Finalize predeal: seat arrivals as `ACTIVE`, rotate dealer, clear hand state. |
| DEAL | PRE_FLOP_BETTING | Always (transient) | Post small blind and big blind. Deal 2 hole cards to each seat with `status == ACTIVE` only (skip `EMPTY`, `JOINED_WAITING`, `FOLDED`). Seats that are sitting out (`JOINED_WAITING`) do **not** receive cards and do not participate in the hand. Set `actionPosition` to seat left of big blind. Set `currentBet = bigBlind`. Increment `handNumber`. |
| PRE_FLOP_BETTING | FLOP | Betting round complete AND `activeSeatCount >= 2` | Collect bets into pot(s). Clear seat bet amounts. Reset `currentBet = 0`. |
| PRE_FLOP_BETTING | HAND_COMPLETE | All but one player folded | Award pot to last remaining player. |
| FLOP | FLOP_BETTING | Always (transient) | Deal 3 community cards. Set `actionPosition` to first active seat left of dealer. |
| FLOP_BETTING | TURN | Betting round complete AND `activeSeatCount >= 2` | Collect bets into pot(s). Clear seat bet amounts. Reset `currentBet = 0`. |
| FLOP_BETTING | HAND_COMPLETE | All but one player folded | Award pot to last remaining player. |
| TURN | TURN_BETTING | Always (transient) | Deal 1 community card. Set `actionPosition` to first active seat left of dealer. |
| TURN_BETTING | RIVER | Betting round complete AND `activeSeatCount >= 2` | Collect bets into pot(s). Clear seat bet amounts. Reset `currentBet = 0`. |
| TURN_BETTING | HAND_COMPLETE | All but one player folded | Award pot to last remaining player. |
| RIVER | RIVER_BETTING | Always (transient) | Deal 1 community card. Set `actionPosition` to first active seat left of dealer. |
| RIVER_BETTING | SHOWDOWN | Betting round complete AND `activeSeatCount >= 2` | Collect bets into pot(s). |
| RIVER_BETTING | HAND_COMPLETE | All but one player folded | Award pot to last remaining player. |
| SHOWDOWN | HAND_COMPLETE | Always (transient) | Evaluate each pot. Award chips to winner(s). Mark winning cards as `showCard = true`. Start review timer (`reviewHandTimeSeconds`). |
| HAND_COMPLETE | (table PAUSED) | `table.status == PAUSE_AFTER_HAND` | Set `table.status = PAUSED`. Run hand cleanup (Section 8.1). |
| HAND_COMPLETE | PREDEAL | `table.status != PAUSE_AFTER_HAND` AND predeal needed | Run hand cleanup (Section 8.1). Enter predeal. |
| HAND_COMPLETE | DEAL | `table.status != PAUSE_AFTER_HAND` AND no predeal needed AND `activeSeatCount >= 2` | Run hand cleanup (Section 8.1). Rotate dealer. Skip predeal and deal immediately. |
| HAND_COMPLETE | WAITING_FOR_PLAYERS | `activeSeatCount < 2` | Run hand cleanup (Section 8.1). |

### "All Active Players Are All-In" Shortcut

When all remaining active players (non-folded) are all-in after a betting round, there is no further action possible.
The remaining community cards are dealt immediately (transitioning through `FLOP`/`TURN`/`RIVER` as transient phases
without betting), and the hand proceeds directly to `SHOWDOWN`.

---

## 4. Betting Round Mechanics

### Action Position

The `actionPosition` on the `Table` tracks the seat index of the player who must act next. Action advances clockwise
(incrementing seat index, wrapping around, skipping `EMPTY`, `FOLDED`, and all-in seats).

```
nextActionPosition(table, currentPosition):
  pos = (currentPosition + 1) % seatCount
  while pos != currentPosition:
    seat = table.seats[pos]
    if seat.status == ACTIVE and not seat.isAllIn:
      return pos
    pos = (pos + 1) % seatCount
  return -1  // No valid next position (everyone else is folded or all-in)
```

### When Is a Betting Round Complete?

A betting round is complete when **all active, non-all-in players have acted AND the current bet is matched by all of
them**. Specifically:

1. Every active, non-all-in player has had at least one opportunity to act in this round.
2. The last aggressive action (bet or raise) has been called by all active players (or they folded/went all-in).
3. Action has returned to the player who made the last aggressive action (or to the first actor if no one bet).

The implementation tracks a `lastRaiserPosition`. When action returns to `lastRaiserPosition`, the round is complete. If
no one bets (all check), the round is complete when action returns to the first actor.

### Pre-Flop vs Post-Flop

| Aspect | Pre-Flop | Post-Flop (Flop/Turn/River) |
|---|---|---|
| First to act | Seat left of big blind (UTG) | First active seat left of dealer |
| Initial bet | Big blind is a forced bet (`currentBet = bigBlind`) | No initial bet (`currentBet = 0`) |
| Big blind option | If no one raises, big blind gets option to check or raise | N/A |
| `lastRaiserPosition` | Initially set to big blind position (to give BB the option) | Initially set to first actor |

### Heads-Up (2-Player) Special Rules

In heads-up play, the standard blind posting changes:

- The **dealer** posts the **small blind**.
- The **non-dealer** posts the **big blind**.
- **Pre-flop**: The dealer (small blind) acts first.
- **Post-flop**: The non-dealer (big blind) acts first (standard "left of dealer" rule still applies).

### All-In Handling

When a player goes all-in:

- Their `isAllIn` flag is set to `true`.
- They cannot be acted upon further (skipped in action rotation).
- If their all-in amount is less than the current bet, it does not reopen betting for players who have already acted
  (the all-in is a "partial call").
- If their all-in amount constitutes a full raise (>= previous raise size), it reopens betting.
- Side pots are created as described in Section 6.

### Valid Actions Per Situation

| Situation | Valid Actions |
|---|---|
| No current bet (or player is big blind pre-flop with no raises) | Check, Bet (>= big blind), Fold |
| Current bet exists, player has enough chips to call | Call, Raise (>= currentBet + minimumRaise), Fold |
| Current bet exists, player cannot afford to call | All-in (call for remaining chips), Fold |
| Player cannot afford the big blind | All-in (for remaining chips) |

The `minimumRaise` equals the size of the last raise. If no raise has occurred, `minimumRaise = bigBlind`. A player can
always go all-in for any amount, even if it's less than the minimum raise.

---

## 5. Player Intent System

Players can pre-submit an intended action **before their turn arrives**. This enables faster play and a better user
experience.

### How It Works

1. A player submits an intent (e.g., "I intend to check" or "I intend to fold") via a table command.
2. The intent is stored on their `Seat.pendingIntent`.
3. When it becomes the player's turn, the system checks the pending intent:
   - **Valid intent**: The action is legal given the current table state. Apply it immediately (no action timer).
   - **Invalid intent**: The game state has changed such that the intent is no longer valid (e.g., the player intended
     to "check" but someone raised). Clear the intent, start the action timer, and wait for the player to submit a new
     action.

### Intent Validation Rules

| Intent | Valid When |
|---|---|
| Fold | Always valid |
| Check | `currentBet == 0` OR player has already matched the current bet |
| Check/Fold | If check is valid, apply check. Otherwise, apply fold. |
| Call | There is a current bet to call |
| Call Any | There is a current bet (calls regardless of amount) |
| Raise to X | Player has enough chips AND X >= currentBet + minimumRaise |

### Clearing Intents

All pending intents for a table are cleared at the start of each new betting round (when community cards are dealt).
This prevents stale intents from a previous round from being accidentally applied.

---

## 6. Side Pot Algorithm

### When Side Pots Are Created

Side pots are created whenever a player goes all-in for less than the current maximum bet. Each pot has a list of seat
positions eligible to win it.

### Peeling Algorithm

After a betting round completes (or at showdown), bets are collected into pots using a "peeling" approach:

```
collectBetsIntoPots(table):
  // Gather all seats that have bet this round (including all-in players)
  bettors = seats where currentBetAmount > 0, sorted by currentBetAmount ascending

  remainingBets = copy of each bettor's currentBetAmount
  pots = table.pots  // may already have pots from previous rounds

  while any remainingBets > 0:
    // Find the smallest remaining bet
    minBet = smallest non-zero value in remainingBets
    potAmount = 0
    eligibleSeats = []

    for each seat in bettors:
      if remainingBets[seat] > 0:
        contribution = min(remainingBets[seat], minBet)
        potAmount += contribution
        remainingBets[seat] -= contribution
        if seat.status == ACTIVE or seat.isAllIn:  // not FOLDED
          eligibleSeats.add(seat.position)

    // Merge into existing pot if eligible seats match the last pot
    if pots is not empty and last pot's eligibleSeats == eligibleSeats:
      lastPot.amount += potAmount
    else:
      pots.add(new Pot(potAmount, eligibleSeats))

  // Clear all seat bet amounts
  for each seat in bettors:
    seat.currentBetAmount = 0
```

### Worked Example

Four players at a table. Blinds are 10/20.

| Seat | Player | Chips Before | Action | Total Bet |
|---|---|---|---|---|
| 0 | Alice | 500 | Raises to 100 | 100 |
| 1 | Bob | 60 | All-in for 60 | 60 |
| 2 | Carol | 500 | Calls 100 | 100 |
| 3 | Dave | 200 | All-in for 200 | 200 |

Alice and Carol call Dave's all-in (total bet 200 each).

Sorted by bet amount: Bob (60), Alice (200), Carol (200), Dave (200).

**Peel 1** (minBet = 60):
- Bob: 60, Alice: 60, Carol: 60, Dave: 60 = **240 chips**
- Eligible: Bob (all-in), Alice, Carol, Dave
- **Main pot**: 240 (seats 0, 1, 2, 3)

**Peel 2** (minBet = 140, since 200 - 60 = 140):
- Alice: 140, Carol: 140, Dave: 140 = **420 chips**
- Eligible: Alice, Carol, Dave (Bob already fully peeled)
- **Side pot 1**: 420 (seats 0, 2, 3)

Remaining: Alice (0), Carol (0), Dave (0). Done.

**Result:**
- Main pot (240): Best hand among Alice, Bob, Carol, Dave wins.
- Side pot 1 (420): Best hand among Alice, Carol, Dave wins.

### Showdown Evaluation Per Pot

During showdown, each pot is evaluated independently:

1. For each pot, consider only the eligible seats.
2. Evaluate the best 5-card hand for each eligible player using their hole cards + community cards.
3. Award the pot to the player(s) with the best hand.
4. If tied, split the pot evenly (remainder chip goes to the player closest to the left of the dealer).

---

## 7. Table Balancing Algorithm

### When Balancing Runs

Table balancing is checked every tick during `transitionGame()` when the game is `ACTIVE`. It runs after all tables have
been transitioned for the current tick.

### Rules

- **Single table** until the player count exceeds `numberOfSeats` (9). A second table is created when the 10th player
  joins.
- Tables should be balanced to within **1 player** of each other.
- A move is triggered when one table has **2+ more players** than another table.

### Player Selection Priority (Who Gets Moved)

When selecting which player to move from an overfull table, prefer (in order):

1. `JOINED_WAITING` (not yet in a hand)
2. The player will be the big blind in the next hand
3. Every other player from the BB position until enough players have been moved.

### Deferred Moves

If the selected player is currently in an active hand (`Seat.status == ACTIVE`), the move is **deferred**:

- A `PendingMove` is recorded on the table: `{ seatPosition, targetTableId }`.
- The move executes during the next `PREDEAL` phase for that table (see Section 8.2).

### Algorithm

```
balanceTables(game):
  tables = game.tables sorted by player count descending
  if tables.size < 2:
    return

  largest = tables[0].numberOfPlayers()
  smallest = tables[last].numberOfPlayers()

  while largest - smallest >= 2:
    sourceTable = table with most players
    targetTable = table with fewest players

    player = selectPlayerToMove(sourceTable)  // priority above

    if player is in active hand:
      sourceTable.pendingMoves.add(PendingMove(player.seatPosition, targetTable.id))
    else:
      executeMove(player, sourceTable, targetTable)

    // Re-evaluate counts (account for pending moves as if executed)
    recalculate largest/smallest

  // Create new table if all tables are full and players are waiting
  if all tables have numberOfPlayers >= numberOfSeats and waitingPlayers exist:
    create new table
    rebalance
```

### Creating Tables

When all tables are at capacity and a new player registers, create a new table and rebalance.

### Merging / Removing Tables

When players leave or bust out, the total player count may drop enough that fewer tables are needed. Rather than
removing tables immediately (which could cause thrashing if new players join shortly after), a **grace period** is used:

1. **Detection**: During each balancing tick, calculate the minimum number of tables needed:
   `minTables = ceil(totalPlayers / numberOfSeats)`. If `game.tables.size > minTables`, the game has excess tables.

2. **Grace period**: When an excess table is first detected, record the timestamp (`tableExcessSince`) on the game. If
   excess tables persist for `tableMergeGraceSeconds` (default: **60 seconds**), proceed with the merge. If the player
   count recovers (new players join) before the grace period elapses, clear the timestamp.

3. **Merge execution**: Select the table with the fewest players as the source. Move all its players to other tables
   (using the same deferred move mechanism if any are mid-hand). Once the source table has 0 players, remove it from
   `game.tables`.

4. **Immediate removal**: A table with 0 players (everyone left or was moved) is removed immediately with no grace
   period -- there's no reason to keep an empty table.

A new `tableMergeGraceSeconds` setting on `GameSettings` controls the grace period.

---

## 8. Between-Hand Processing (HAND_COMPLETE and PREDEAL Phases)

Between-hand work is split across two phases:

- **HAND_COMPLETE** handles cleanup from the just-finished hand (review period, clearing state, removing departed
  players, marking busted players).
- **PREDEAL** (conditional) handles preparation for the next hand (buy-ins/rebuys, table balancing moves, seating new
  arrivals, rotating the dealer).

This split gives players an explicit, observable window to buy in or rebuy before the next hand starts. If no predeal
work is needed (no pending buy-ins, no table moves, no new arrivals), the table skips `PREDEAL` entirely and goes
straight from `HAND_COMPLETE` to `DEAL`.

### 8.1 HAND_COMPLETE: Hand Cleanup

These steps run in order during the `HAND_COMPLETE` phase:

#### 8.1.1 Review Period

If the hand went to showdown, wait `reviewHandTimeSeconds` (8 seconds) before proceeding. This gives players time to
see the results and optionally show/muck their cards. The `HAND_COMPLETE` phase persists across ticks until the review
period elapses.

During the review period, players can submit `ShowCards` commands to reveal their hole cards.

#### 8.1.2 Remove Departed Players

- Players who issued `LeaveGame` during the hand: remove from seat, set seat to `EMPTY`.
- Players whose `Player.status == AWAY` and have been away for longer than a configurable timeout: remove from seat.

#### 8.1.3 Handle Busted Players (Cash Game)

Players whose `chipCount == 0` after the hand:
- Set `Seat.status = JOINED_WAITING` (they remain seated but sit out).
- Set `Player.status = BUYING_IN` to indicate they need to re-buy.
- The player must submit a `BuyIn` command during `PREDEAL` (or later) to get more chips.
- There is no auto-removal in cash games (players can re-buy at any time).

#### 8.1.4 Clear Hand State

- Clear all `Seat.cards` (hole cards).
- Clear `Seat.action` for all seats.
- Clear `communityCards`.
- Clear `pots`.
- Reset `currentBet = 0`, `minimumRaise = bigBlind`.
- Clear all `Seat.pendingIntent`.
- Reset `Seat.isAllIn = false` for all seats.
- Reset `Seat.currentBetAmount = 0` for all seats.

#### 8.1.5 Check Game-Level Signals

If `table.status == PAUSE_AFTER_HAND`:
- Set `table.status = PAUSED`.
- Set `table.handPhase = WAITING_FOR_PLAYERS`.
- Do not start a new hand. Stop here.

#### 8.1.6 Determine Next Phase

Check if predeal processing is needed. Predeal is needed if **any** of the following are true:
- Any seat has `Player.status == BUYING_IN` (player needs to buy in / rebuy).
- There are pending table balance moves (`table.pendingMoves` is not empty).
- Any seat has `Seat.status == JOINED_WAITING` (new arrival waiting to be seated into the next hand).
- A player has indicated they want to add-on (cash game top-up).

If predeal is needed: transition to `PREDEAL`.
If predeal is not needed AND `activeSeatCount >= 2`: rotate dealer button, transition to `DEAL`.
If `activeSeatCount < 2`: transition to `WAITING_FOR_PLAYERS`.

### 8.2 PREDEAL: Next-Hand Preparation

The `PREDEAL` phase gives players time to complete actions before the next hand begins. It persists across ticks until
all pending work is resolved or a configurable timer (`predealTimeSeconds`) expires.

#### 8.2.1 What Triggers PREDEAL

PREDEAL is entered from `HAND_COMPLETE` (after cleanup) or from `WAITING_FOR_PLAYERS` (when players join a table that
was idle). It is only entered when there is work to do.

#### 8.2.2 What Happens During PREDEAL

On each tick while in `PREDEAL`:

1. **Accept buy-ins**: Players with `Player.status == BUYING_IN` can submit `BuyIn` commands. When a buy-in is
   processed, set `Player.status = ACTIVE`, add chips to `chipCount`, and set `Seat.status = JOINED_WAITING` (ready to
   play next hand).

2. **Execute pending table balance moves**: For each `PendingMove` on the table, remove the player from their current
   seat (set seat to `EMPTY`) and assign them to a random empty seat on the target table with status `JOINED_WAITING`.

3. **Check resolution**: PREDEAL is considered resolved when:
   - No players have `Player.status == BUYING_IN`, OR the predeal timer has expired.
   - All pending moves have been executed.
   - (Players who haven't bought in by the timer expiration simply sit out the next hand.)

#### 8.2.3 PREDEAL Exit

When PREDEAL resolves:

1. **Rotate dealer button**: Advance `dealerPosition` clockwise to the next active seat (skip `EMPTY`,
   `JOINED_WAITING`). If this is the first hand at the table (`dealerPosition == null`), assign the dealer to a random
   active seat. Compute the small blind and big blind positions from the new dealer position.

2. **Seat new arrivals (with blind rule)**: For each seat with `Seat.status == JOINED_WAITING`:
   - Determine where the seat falls relative to the blinds.
   - If the seat is positioned **between the dealer and the big blind** (i.e., in the small blind or big blind
     position), the player **must sit out** this hand. Set `Seat.mustPostBlind = true` and leave the seat as
     `JOINED_WAITING`. The player will be activated on a subsequent hand once the dealer button has passed their
     position.
   - Otherwise, transition the seat to `ACTIVE`. The player participates in the next hand.
   - **Exception**: On the very first hand at a table (all players are new), this rule does not apply -- all
     `JOINED_WAITING` seats transition to `ACTIVE`.

3. **Transition**: If `activeSeatCount >= 2`, advance to `DEAL`. Otherwise, go to `WAITING_FOR_PLAYERS`.

**Why this rule exists**: In casino-style cash games, a new player arriving at a table cannot immediately play from a
favorable late position without first paying the big blind. This prevents players from "free-riding" through the blinds.
The player must wait for the button to pass their seat (or optionally post a blind to play immediately -- see below).

**Optional: Post-to-play**: As an alternative to sitting out, a new arrival can choose to "post" (pay a big blind out of
position) to enter the hand immediately. This is handled via a `PostBlind` command during PREDEAL. If the player posts,
set `Seat.mustPostBlind = false`, deduct the big blind from their chips, add it to the pot, and transition the seat to
`ACTIVE`. This is a common casino convention but can be enabled/disabled via a game setting
(`allowPostToPlay`, default: `true`).

### Returning Players and Missed Blinds

When a player returns from sitting out (e.g., was `AWAY`, busted and re-bought, or voluntarily sat out), the blinds may
have passed their position while they were inactive. To prevent free-riding, the **missed blind rule** applies:

1. **Tracking**: When a player sits out (`JOINED_WAITING`), record whether the big blind has passed their seat position
   while they were out. This is tracked via `Seat.missedBigBlind` (boolean, set to `true` when the big blind advances
   past the seat's position during a hand where the seat was inactive).

2. **Re-entry requirement**: When the player is ready to rejoin (bought in, came back from AWAY, etc.) during PREDEAL:
   - If `Seat.missedBigBlind == true`: The player **must post a big blind** to re-enter. This is a "dead blind" -- it
     goes into the pot but does not count as the player's bet (they still owe any subsequent bets). Set
     `Seat.missedBigBlind = false` after posting.
   - If `Seat.missedBigBlind == false` (the blinds haven't passed yet): The player can re-enter without posting.

3. **When the blind is posted**: The dead blind is collected during the `DEAL` phase along with the regular blinds. The
   posted amount is added to the pot but the player's `currentBetAmount` starts at 0 (it's a dead bet, not a live one).

4. **Simplification for home games**: Since this is a home poker server, you may want a game setting
   (`requireMissedBlindPost`, default: `false`) to disable this rule entirely. When disabled, returning players simply
   rejoin on the next hand without posting -- appropriate for casual home games where strict blind enforcement feels
   heavy-handed.

### 8.3 PREDEAL Timer

A new `predealTimeSeconds` setting on `GameSettings` controls how long the table waits in `PREDEAL`. Suggested default:
**15 seconds**. This is long enough for a player to confirm a rebuy through the UI, but short enough to keep the game
moving.

If all pending actions resolve before the timer, `PREDEAL` advances immediately (no unnecessary waiting).

---

## 9. Edge Cases

### Player Disconnects

- When a player disconnects, set `Player.status = AWAY`.
- If it is their turn to act, the action timer still runs.
- On timeout, the player's action defaults to:
  - **Check** if no bet is required (free check).
  - **Fold** if a bet is required.
- After a configurable number of consecutive hands idle (or time limit), the player may be marked `OUT` and their seat
  freed.

### All Fold to One Player

When all players but one have folded:
- The remaining player wins the entire pot immediately.
- Skip `SHOWDOWN` (the winner's cards are not revealed unless they choose to show).
- Proceed directly to `HAND_COMPLETE`.

### Insufficient Chips for Blind

If a player cannot afford the full blind:
- They go all-in for their remaining chips as the blind.
- They are eligible for a main pot equal to their contribution multiplied by the number of callers.
- A side pot is created for the remaining blind amount and subsequent bets.

### Heads-Up Blind Posting

In 2-player (heads-up) play:
- The dealer posts the small blind.
- The other player posts the big blind.
- Pre-flop: the dealer acts first.
- Post-flop: the big blind (non-dealer) acts first.

When transitioning from 3+ players to heads-up (a player busts/leaves):
- The player who was big blind retains the big blind.
- The other player becomes dealer and posts the small blind.
- This prevents any player from avoiding the big blind.

### Player Busts in Cash Game

- After losing all chips, the player's seat status becomes `JOINED_WAITING` and `Player.status = BUYING_IN`.
- The table enters `PREDEAL`, giving the player a `predealTimeSeconds` window to submit a `BuyIn` command.
- If they buy in during `PREDEAL`, they rejoin immediately for the next hand.
- If they don't buy in before `PREDEAL` expires, they sit out (`JOINED_WAITING`) and can buy in at any later `PREDEAL`.
- There is no auto-removal in cash games (players can re-buy at any time).

### Command Arriving After Phase Change

Commands are validated against the **current** table state when applied. If a player submits a `Call` command but by the
time it's processed the hand has moved to a new phase (or the player is no longer in the hand), the command is rejected
with a `ValidationException` and a `UserMessage` event is emitted.

This is inherently handled by the architecture: commands are drained and applied at the start of each tick, and state
transitions happen after. Within a single tick, the state is consistent.

### Multiple Players All-In at Different Amounts

When multiple players go all-in for different amounts in the same round, the side pot algorithm (Section 6) handles this
correctly by peeling from the smallest to the largest contribution. Each all-in amount creates a pot boundary.

### Split Pot with Odd Chips

When a pot cannot be evenly split between tied winners:
- Divide as evenly as possible.
- The remainder chip(s) go to the winning player closest to the **left of the dealer** (clockwise).

---

## 10. Model Changes Required

### New: `HandPhase` Enum

```java
// poker-common: org.homepoker.model.game.HandPhase
public enum HandPhase {
  WAITING_FOR_PLAYERS,
  PREDEAL,
  DEAL,
  PRE_FLOP_BETTING,
  FLOP,
  FLOP_BETTING,
  TURN,
  TURN_BETTING,
  RIVER,
  RIVER_BETTING,
  SHOWDOWN,
  HAND_COMPLETE
}
```

### Modified: `Table`

New fields (using existing Lombok `@Data @Builder @Accessors(fluent = true)` pattern):

```java
private HandPhase handPhase;           // Current phase within a hand
private int currentBet;                // Current bet amount that must be matched
private int minimumRaise;              // Minimum raise increment (initially bigBlind)
private @Nullable Instant phaseStartedAt;  // When the current phase began (for timeouts)
private @Nullable Instant actionDeadline;  // Deadline for current player's action
private @Nullable Integer smallBlindPosition;  // Seat index of small blind
private @Nullable Integer bigBlindPosition;    // Seat index of big blind
private @Nullable Integer lastRaiserPosition;  // Seat index of last raiser (for round completion)
private int handNumber;                // Monotonically increasing hand counter
private List<PendingMove> pendingMoves;  // Deferred table balance moves
```

Default for `handPhase` in builder: `HandPhase.WAITING_FOR_PLAYERS`.

### Modified: `CashGame` (Game-Level)

New field:

```java
private @Nullable Instant tableExcessSince;  // When excess tables were first detected (for merge grace period)
```

This is game-level state (not per-table) because table merging is a cross-table decision.

### Modified: `GameSettings`

New field:

```java
int predealTimeSeconds;          // Max time to wait in PREDEAL for buy-ins (default: 15)
int tableMergeGraceSeconds;      // Grace period before merging excess tables (default: 60)
boolean allowPostToPlay;         // Allow new arrivals to post a blind to play immediately (default: true)
boolean requireMissedBlindPost;  // Returning players must post BB if blinds passed them (default: false)
```

New record on `Table`:

```java
public record PendingMove(int seatPosition, String targetTableId) {}
```

### Modified: `Seat`

New fields:

```java
private int currentBetAmount;          // Amount this seat has bet in the current round
private boolean isAllIn;               // Whether this seat is all-in
private boolean mustPostBlind;         // New arrival must wait for button to pass (or post to play)
private boolean missedBigBlind;        // Big blind passed this seat while player was sitting out
private @Nullable PlayerAction pendingIntent;  // Pre-submitted action intent
```

### New Commands

These are new `@GameCommandMarker` record classes implementing `GameCommand` or `TableCommand`:

| Command | Interface | Fields | Description |
|---|---|---|---|
| `StartGame` | `GameCommand` | `gameId, user` | Admin starts the game (SEATING -> ACTIVE) |
| `PauseGame` | `GameCommand` | `gameId, user` | Admin pauses the game |
| `ResumeGame` | `GameCommand` | `gameId, user` | Admin resumes the game |
| `BuyIn` | `GameCommand` | `gameId, user, amount` | Player buys in or re-buys |
| `LeaveGame` | `GameCommand` | `gameId, user` | Player leaves an active game |
| `PlayerActionCommand` | `TableCommand` | `gameId, tableId, user, action (PlayerAction)` | Player submits a game action (fold, check, call, bet, raise) |
| `PlayerIntent` | `TableCommand` | `gameId, tableId, user, action (PlayerAction)` | Player pre-submits an intended action |
| `ShowCards` | `TableCommand` | `gameId, tableId, user` | Player chooses to show cards at showdown |
| `PostBlind` | `TableCommand` | `gameId, tableId, user` | New arrival posts big blind to play immediately (instead of waiting for button to pass) |

### New Events

New `@EventMarker` record classes implementing `TableEvent` or `GameEvent`:

| Event | Interface | Key Fields | Description |
|---|---|---|---|
| `HandStarted` | `TableEvent` | `gameId, tableId, handNumber, dealerPosition` | A new hand has begun |
| `HoleCardsDealt` | `UserEvent + TableEvent` | `gameId, tableId, userId, cards` | Player receives their hole cards (private per player) |
| `CommunityCardsDealt` | `TableEvent` | `gameId, tableId, cards, phase (FLOP/TURN/RIVER)` | Community cards revealed |
| `PlayerActed` | `TableEvent` | `gameId, tableId, seatPosition, action` | A player took an action |
| `PlayerTimedOut` | `TableEvent` | `gameId, tableId, seatPosition, defaultAction` | A player's timer expired |
| `BettingRoundComplete` | `TableEvent` | `gameId, tableId, pots` | A betting round finished |
| `ShowdownResult` | `TableEvent` | `gameId, tableId, potResults (list of pot + winners + hand rank)` | Hand evaluation results |
| `HandComplete` | `TableEvent` | `gameId, tableId, handNumber` | Hand is fully complete |
| `PlayerMoved` | `GameEvent` | `gameId, userId, fromTableId, toTableId` | Player was moved for table balancing |
| `PlayerBuyIn` | `GameEvent` | `gameId, userId, amount, newChipCount` | Player bought in |
| `GameStatusChanged` | `GameEvent` | `gameId, oldStatus, newStatus` | Game status transitioned |

### Deck Abstraction

The existing `Deck` class (auto-shuffled 52-card deck with `drawCards(n)`) is sufficient. A `Deck` instance is created
at the start of each hand (in the `DEAL` phase) and used throughout the hand. The deck is **not persisted** on the
`Table` model -- it is a transient object held by `TexasHoldemTableManager` during hand processing. Since the game loop
is single-threaded per game, this is safe.

### Note on `Table.Status` vs `HandPhase`

The existing `Table.Status` enum (`PAUSED`, `PLAYING`, `PAUSE_AFTER_HAND`) serves as an **administrative control**:

- `PAUSED`: Table is idle. No hands are dealt.
- `PLAYING`: Table is actively running hands.
- `PAUSE_AFTER_HAND`: Table will pause when the current hand completes.

The new `HandPhase` tracks **where within a hand** the table is. These are orthogonal:

- A table can be `PLAYING` with `handPhase = WAITING_FOR_PLAYERS` (active but not enough players).
- A table can be `PAUSE_AFTER_HAND` with `handPhase = FLOP_BETTING` (finishing the current hand before pausing).
- A table that is `PAUSED` should have `handPhase = WAITING_FOR_PLAYERS`.

