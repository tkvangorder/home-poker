# Table-State Skill

## Description

Guides implementation of table-level hand progression in the poker server. Covers the `HandPhase` state machine (WAITING_FOR_PLAYERS through SHOWDOWN), betting round mechanics, the side pot algorithm, the player intent system, and between-hand processing (HAND_COMPLETE and PREDEAL).

## Instructions

You are implementing table-level state transitions for Texas Hold'em hands. Before writing any code, read the full design doc and the existing source files listed below to understand the architecture.

### Design Reference

Read `cash-game-state-management.md` — specifically:
- **Section 3**: Table-Level State Machine (`HandPhase` enum, transition table, transient vs waiting vs betting phases, all-in shortcut)
- **Section 4**: Betting Round Mechanics (action position, round completion, pre-flop vs post-flop, heads-up rules, all-in handling, valid actions)
- **Section 5**: Player Intent System (pre-submitted actions, validation rules, clearing)
- **Section 6**: Side Pot Algorithm (peeling approach, worked example, showdown evaluation per pot)
- **Section 8**: Between-Hand Processing (HAND_COMPLETE cleanup, PREDEAL preparation, blind rules for new arrivals, missed blind rule)
- **Section 9**: Edge Cases (disconnects, all fold, insufficient chips for blind, heads-up blind posting, player busts, command timing)
- **Section 10**: Model Changes Required (new fields on Table, Seat, new enums, new commands/events)

### Key Files to Read First

| File | Purpose |
|---|---|
| `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` | **Primary implementation target** — `transitionTable()` is currently empty |
| `poker-server/src/main/java/org/homepoker/game/table/TableManager.java` | Abstract base — `applyCommand()` routes table commands, `applySubcommand()` hook |
| `poker-server/src/main/java/org/homepoker/game/GameManager.java` | Game loop — calls `tableManager.transitionTable()` each tick for every table |
| `poker-server/src/main/java/org/homepoker/game/GameContext.java` | Per-tick event accumulator; use `gameContext.queueEvent()` for all events |
| `poker-server/src/main/java/org/homepoker/game/GameSettings.java` | Timing constants: `actionTimeSeconds` (30), `reviewHandTimeSeconds` (8) |
| `poker-common/src/main/java/org/homepoker/model/game/Table.java` | Table model — needs new fields for `HandPhase`, `currentBet`, etc. |
| `poker-common/src/main/java/org/homepoker/model/game/Seat.java` | Seat model — needs new fields for `currentBetAmount`, `isAllIn`, etc. |
| `poker-common/src/main/java/org/homepoker/model/game/Player.java` | Player model with `chipCount`, `PlayerStatus` |
| `poker-common/src/main/java/org/homepoker/model/game/PlayerAction.java` | Sealed interface: `Fold`, `Check`, `Call(amount)`, `Bet(amount)`, `Raise(amount)` |
| `poker-common/src/main/java/org/homepoker/model/command/TableCommand.java` | Table command interface (extends `GameCommand`, adds `tableId()`) |
| `poker-common/src/main/java/org/homepoker/model/command/GameCommand.java` | Command interface with `@GameCommandMarker` annotation |
| `poker-common/src/main/java/org/homepoker/model/event/TableEvent.java` | Table event interface (extends `GameEvent`, adds `tableId()`) |
| `poker-common/src/main/java/org/homepoker/model/event/UserEvent.java` | User event interface (for private per-player events like hole cards) |
| `poker-common/src/main/java/org/homepoker/model/poker/Card.java` | Card model |
| `poker-common/src/main/java/org/homepoker/model/poker/Deck.java` | Auto-shuffled 52-card deck with `drawCards(n)` |
| `poker-server/src/main/java/org/homepoker/poker/BitwisePokerRanker.java` | Hand ranking engine — use for showdown evaluation |
| `poker-common/src/main/java/org/homepoker/lib/exception/ValidationException.java` | Thrown for invalid commands |

### What Needs to Be Implemented

#### 1. New HandPhase Enum (poker-common)

Create `org.homepoker.model.game.HandPhase`:

```java
public enum HandPhase {
  WAITING_FOR_PLAYERS,  // No hand in progress, waiting for 2+ active players
  PREDEAL,              // Conditional: pause for buy-ins, rebuys, table moves
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

#### 2. New Table Command Records (poker-common)

Create in `org.homepoker.model.command`, annotated with `@GameCommandMarker`:

```java
// Player submits a game action (fold, check, call, bet, raise)
public record PlayerActionCommand(String gameId, String tableId, User user, PlayerAction action)
    implements TableCommand {}

// Player pre-submits an intended action before their turn
public record PlayerIntent(String gameId, String tableId, User user, PlayerAction action)
    implements TableCommand {}

// Player chooses to show cards during HAND_COMPLETE review period
public record ShowCards(String gameId, String tableId, User user)
    implements TableCommand {}

// New arrival posts big blind to play immediately instead of waiting
public record PostBlind(String gameId, String tableId, User user)
    implements TableCommand {}
```

Follow the pattern of existing command records (see `EndGame.java`).

#### 3. New Table Event Records (poker-common)

Create in `org.homepoker.model.event`, annotated with `@EventMarker`:

| Event | Interface | Fields |
|---|---|---|
| `HandStarted` | `TableEvent` | `timestamp, gameId, tableId, handNumber, dealerPosition` |
| `HoleCardsDealt` | `UserEvent` + `TableEvent` | `timestamp, gameId, tableId, userId, cards (List<Card>)` |
| `CommunityCardsDealt` | `TableEvent` | `timestamp, gameId, tableId, cards (List<Card>), phase (HandPhase)` |
| `PlayerActed` | `TableEvent` | `timestamp, gameId, tableId, seatPosition, action (PlayerAction)` |
| `PlayerTimedOut` | `TableEvent` | `timestamp, gameId, tableId, seatPosition, defaultAction (PlayerAction)` |
| `BettingRoundComplete` | `TableEvent` | `timestamp, gameId, tableId, pots (List<Table.Pot>)` |
| `ShowdownResult` | `TableEvent` | `timestamp, gameId, tableId, potResults (list of pot + winners + hand rank)` |
| `HandComplete` | `TableEvent` | `timestamp, gameId, tableId, handNumber` |

Follow the pattern of `GameMessage.java`.

#### 4. Model Changes

**Table.java** — add new fields:
```java
private HandPhase handPhase;                    // default: WAITING_FOR_PLAYERS
private int currentBet;                         // current bet to match
private int minimumRaise;                       // min raise increment (initially bigBlind)
private @Nullable Instant phaseStartedAt;       // when current phase began (for timeouts)
private @Nullable Instant actionDeadline;       // deadline for current player's action
private @Nullable Integer smallBlindPosition;   // seat index of small blind
private @Nullable Integer bigBlindPosition;     // seat index of big blind
private @Nullable Integer lastRaiserPosition;   // for round completion detection
private int handNumber;                         // monotonically increasing hand counter
private List<PendingMove> pendingMoves;         // deferred table balance moves

public record PendingMove(int seatPosition, String targetTableId) {}
```

**Seat.java** — add new fields:
```java
private int currentBetAmount;                   // amount bet in current round
private boolean isAllIn;                        // whether seat is all-in
private boolean mustPostBlind;                  // new arrival must wait or post to play
private boolean missedBigBlind;                 // BB passed while sitting out
private @Nullable PlayerAction pendingIntent;   // pre-submitted action intent
```

#### 5. Table-Level State Transitions (TexasHoldemTableManager.transitionTable)

This is the core implementation. The `transitionTable()` method is called once per tick per table. Implement the full `HandPhase` state machine from Section 3.

**Phase categories:**
- **Transient phases** (DEAL, FLOP, TURN, RIVER, SHOWDOWN, HAND_COMPLETE): Complete within a single tick. Perform their action and immediately advance. Chain multiple transient phases in a single tick (e.g., DEAL immediately transitions to PRE_FLOP_BETTING).
- **Waiting phases** (PREDEAL): Persist across ticks. Have a configurable timer.
- **Betting phases** (PRE_FLOP_BETTING, FLOP_BETTING, TURN_BETTING, RIVER_BETTING): Persist across ticks. Wait for player action or timeout.

**Implementation structure** (suggested — use a loop for transient chaining):

```java
public void transitionTable(Game<T> game, Table table, GameContext gameContext) {
  if (table.status() == Table.Status.PAUSED) return;

  boolean advanced = true;
  while (advanced) {
    advanced = switch (table.handPhase()) {
      case WAITING_FOR_PLAYERS -> transitionFromWaiting(game, table, gameContext);
      case PREDEAL             -> transitionFromPredeal(game, table, gameContext);
      case DEAL                -> transitionFromDeal(game, table, gameContext);
      case PRE_FLOP_BETTING    -> transitionFromBetting(game, table, gameContext);
      case FLOP                -> transitionFromFlop(game, table, gameContext);
      case FLOP_BETTING        -> transitionFromBetting(game, table, gameContext);
      case TURN                -> transitionFromTurn(game, table, gameContext);
      case TURN_BETTING        -> transitionFromBetting(game, table, gameContext);
      case RIVER               -> transitionFromRiver(game, table, gameContext);
      case RIVER_BETTING       -> transitionFromBetting(game, table, gameContext);
      case SHOWDOWN            -> transitionFromShowdown(game, table, gameContext);
      case HAND_COMPLETE       -> transitionFromHandComplete(game, table, gameContext);
    };
  }
}
```

Each method returns `true` if it advanced the phase (so transient phases chain), `false` if the phase persists (betting waiting for input, predeal waiting for timer).

#### 6. Betting Round Implementation

Key mechanics from Section 4:

**Action position tracking:**
```
nextActionPosition(table, currentPosition):
  pos = (currentPosition + 1) % seatCount
  while pos != currentPosition:
    seat = table.seats[pos]
    if seat.status == ACTIVE and not seat.isAllIn:
      return pos
    pos = (pos + 1) % seatCount
  return -1  // everyone else folded or all-in
```

**Round completion:** A betting round is complete when action returns to `lastRaiserPosition` (all active non-all-in players have matched or folded). If no one bet, complete when action returns to first actor.

**Pre-flop vs post-flop differences:**

| Aspect | Pre-Flop | Post-Flop |
|---|---|---|
| First to act | Left of big blind (UTG) | First active seat left of dealer |
| Initial bet | `currentBet = bigBlind` | `currentBet = 0` |
| Big blind option | BB gets check/raise if no raise | N/A |
| `lastRaiserPosition` | Set to BB position | Set to first actor |

**Heads-up (2-player) special rules:**
- Dealer posts small blind, non-dealer posts big blind
- Pre-flop: dealer acts first
- Post-flop: non-dealer acts first

**Valid actions per situation:**

| Situation | Valid Actions |
|---|---|
| No current bet (or BB pre-flop with no raises) | Check, Bet (>= bigBlind), Fold |
| Current bet exists, can afford to call | Call, Raise (>= currentBet + minimumRaise), Fold |
| Current bet exists, can't afford to call | All-in (remaining chips), Fold |
| Can't afford big blind | All-in (remaining chips) |

**All-in handling:**
- Set `isAllIn = true`, skip in action rotation
- Partial call (less than current bet) does NOT reopen betting
- Full raise (>= previous raise size) DOES reopen betting

#### 7. Player Intent System (Section 5)

When it's a player's turn, check `seat.pendingIntent`:
- Valid intent: apply immediately (no timer)
- Invalid intent (state changed): clear it, start action timer

**Intent validation:**

| Intent | Valid When |
|---|---|
| Fold | Always |
| Check | `currentBet == 0` or player has matched |
| Check/Fold | Check if valid, else fold |
| Call | There is a current bet |
| Call Any | There is a current bet |
| Raise to X | Enough chips AND X >= currentBet + minimumRaise |

Clear all intents at the start of each new betting round.

#### 8. Side Pot Algorithm (Section 6)

Implement the peeling algorithm for collecting bets into pots:

1. Gather all seats with `currentBetAmount > 0`, sort ascending
2. Peel from smallest to largest: each distinct bet level creates a pot boundary
3. Merge into previous pot if eligible seats match
4. Clear all `currentBetAmount` after collection

At showdown, evaluate each pot independently using `BitwisePokerRanker`. Award to best hand among eligible seats. Split ties evenly (remainder chip to player left of dealer).

#### 9. HAND_COMPLETE Processing (Section 8.1)

In order:
1. **Review period**: If hand went to showdown, wait `reviewHandTimeSeconds` (8s). Accept `ShowCards` commands during this window.
2. **Remove departed players**: Players who issued `LeaveGame` — set seat to `EMPTY`.
3. **Handle busted players**: `chipCount == 0` -> `Seat.status = JOINED_WAITING`, `Player.status = BUYING_IN`.
4. **Clear hand state**: Clear cards, community cards, pots, bets, intents, all-in flags.
5. **Check game-level signals**: If `table.status == PAUSE_AFTER_HAND` -> set `PAUSED`, set `handPhase = WAITING_FOR_PLAYERS`, stop.
6. **Determine next phase**: Check if PREDEAL needed (buy-ins, pending moves, new arrivals). If yes -> PREDEAL. If no and 2+ active -> rotate dealer, DEAL. If <2 active -> WAITING_FOR_PLAYERS.

#### 10. PREDEAL Processing (Section 8.2)

Each tick while in PREDEAL:
1. **Accept buy-ins**: Process `BuyIn` commands — set `Player.status = ACTIVE`, add chips, set `Seat.status = JOINED_WAITING`.
2. **Execute pending moves**: Remove player from seat, assign to target table.
3. **Check resolution**: No more `BUYING_IN` players (or timer expired) AND all moves executed.

On PREDEAL exit:
1. **Rotate dealer**: Advance clockwise to next active seat. First hand: random active seat.
2. **Seat new arrivals** (blind rule): Players between dealer and BB must sit out (`mustPostBlind = true`) unless it's the first hand. Optional `PostBlind` command to enter immediately.
3. **Transition**: If 2+ active -> DEAL. Else -> WAITING_FOR_PLAYERS.

#### 11. Command Handling in TableManager

Override `applySubcommand()` in `TexasHoldemTableManager`:

```java
case PlayerActionCommand c -> handlePlayerAction(c, game, table, gameContext);
case PlayerIntent c        -> handlePlayerIntent(c, game, table, gameContext);
case ShowCards c            -> handleShowCards(c, game, table, gameContext);
case PostBlind c            -> handlePostBlind(c, game, table, gameContext);
```

Validate commands against current table state. For `PlayerActionCommand`:
- Verify it's the player's turn (`table.actionPosition` matches their seat)
- Verify the action is valid for the current situation
- Apply the action, update seat state, advance action position
- Check if betting round is complete

#### 12. All-In Shortcut

When all remaining active (non-folded) players are all-in after a betting round, deal remaining community cards as transient phases (no betting) and go directly to SHOWDOWN.

### Patterns to Follow

- **Commands**: `@GameCommandMarker` record implementing `TableCommand`. Fields: `gameId`, `tableId`, `user`, plus command-specific fields.
- **Events**: `@EventMarker` record implementing `TableEvent`. Must include `timestamp`, `gameId`, `tableId`.
- **Validation**: Throw `ValidationException` — caught by game loop, converted to `UserMessage`.
- **State mutation**: All on single game loop thread. No synchronization needed.
- **Event queueing**: `gameContext.queueEvent(new SomeEvent(...))`.
- **Deck**: Create `new Deck()` at start of each hand (DEAL phase). Transient object, not persisted on Table.
- **Hand evaluation**: Use `BitwisePokerRanker` for showdown. It evaluates best 5-card hand from any number of cards.

### Testing Guidance

**Unit tests** (preferred for table logic):
- Build `Table` and `Seat` state directly using builders
- Create a `GameContext` with `GameSettings.TEXAS_HOLDEM_SETTINGS`
- Call `transitionTable()` or command handlers directly
- Assert phase transitions, seat states, pot amounts, event emissions
- Test each phase transition independently
- Test betting round completion detection
- Test side pot algorithm with the worked example from Section 6
- Test heads-up special rules
- Test all-in scenarios
- Test player intent validation

**Integration tests**:
- Extend `BaseIntegrationTest`
- Use `SINGLE_THREAD` mode (from `application-test.yml`) for deterministic behavior
- Call `processGameTick()` explicitly to advance state

### Implementation Order

1. Create `HandPhase` enum
2. Add new fields to `Table` and `Seat` models
3. Create table command records (`PlayerActionCommand`, `PlayerIntent`, `ShowCards`, `PostBlind`)
4. Create table event records (`HandStarted`, `HoleCardsDealt`, etc.)
5. Implement WAITING_FOR_PLAYERS -> DEAL transition (simplest path, skip PREDEAL initially)
6. Implement DEAL phase (blind posting, card dealing)
7. Implement betting round mechanics (action tracking, round completion, valid actions)
8. Implement FLOP/TURN/RIVER transient phases
9. Implement SHOWDOWN (hand evaluation, pot awarding using `BitwisePokerRanker`)
10. Implement HAND_COMPLETE (cleanup, review period)
11. Implement side pot algorithm
12. Implement PREDEAL phase (buy-ins, dealer rotation, blind rules)
13. Implement player intent system
14. Implement heads-up special rules
15. Implement all-in shortcut (deal remaining cards when all players all-in)
16. Implement command handling (`PlayerActionCommand`, `ShowCards`, `PostBlind`)
17. Write unit tests for each component
18. Run `./gradlew clean build` to verify