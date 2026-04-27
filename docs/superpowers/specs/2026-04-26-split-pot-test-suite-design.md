# Split-Pot Test Suite Design

**Date:** 2026-04-26
**Status:** Approved (pending user spec review)

## Goal

Add an extensive, deterministic test suite that verifies the poker server awards the correct chips to the correct players when a hand produces multiple side pots from multiple all-ins. Cover 3-pot and 4-pot scenarios, including ties (chops within a pot) and folded contributors (a player who put chips in a pot but folded before showdown).

## Why

Existing all-in tests in `TexasHoldemTableManagerTest` (e.g. `fullHand_allInPreFlop_dealsAllCardsAndShowdown`, `sidePot_createdWhenPlayerGoesAllIn`) only assert *structural* outcomes (pots created, chip counts conserved). They cannot assert *who wins* because the production `Deck` is randomly shuffled and offers no seeding API. As a result, the showdown distribution code (`evaluatePotWinners()` in `TexasHoldemTableManager`, lines 876-929) is not directly verified end-to-end. This is the part of the codebase most likely to silently misallocate chips in multi-pot scenarios — exactly what this suite targets.

## Out of Scope

- Tournament-specific behavior (rebuy, blind escalation, etc.)
- Rake calculation
- WebSocket / network delivery of `ShowdownResult`
- Changes to `evaluatePotWinners()` or `collectBetsIntoPots()` themselves; this is purely a test addition (plus the minimum production seam needed to make tests deterministic)

## Architecture

### New components

**1. Stackable `Deck` (small production change)**

Add a constructor `Deck(List<Card> stackedCards)` that skips shuffling and draws cards in the order given. The default constructor and `fromRemainingCards()` are unchanged. `drawCards(n)` already pops from the front, so its behavior is identical otherwise.

To wire this into `TexasHoldemTableManager` without leaking test concerns, the manager gains a `Supplier<Deck>` field defaulting to `Deck::new`. Tests inject a supplier that returns a stacked deck. The supplier is invoked once per hand, where the manager currently constructs a `Deck`.

This is the only production code change in the plan. Everything else lives under `src/test/`.

**2. `DeckBuilder` (test helper)**

Lives in `poker-server/src/test/java/org/homepoker/test/DeckBuilder.java`. Builds a stacked deck arranged in correct Texas Hold'em deal order so a test reads as the dealer would deal:

```java
Deck deck = DeckBuilder.holdem(numPlayers)
    .holeCards(1, "As Ks")    // seat 1 hole cards
    .holeCards(2, "Qd Qc")
    .holeCards(3, "8h 8d")
    .holeCards(4, "7c 2d")
    .flop("Js Ts 2c")
    .turn("3d")
    .river("4h")
    .build();
```

Internals:
- Interleaves hole cards in the standard order (one card to each seat, then a second card to each), starting from seat 1 (the small blind in the test fixture's setup).
- Inserts burn cards (any unused card from the remaining deck) before flop, turn, river to match real dealing.
- Pads the back of the deck with arbitrary remaining cards so any extra `drawCards()` calls succeed.
- Validates: every card is unique, every seat has exactly two hole cards specified, flop has 3 cards, turn and river each 1 card.
- Card parsing: simple `"As"` = ace of spades, `"Td"` = ten of diamonds (`A,K,Q,J,T,9..2` × `s,h,d,c`).

**3. `SplitPotScenarioFixture` (test fixture builder)**

Lives in `poker-server/src/test/java/org/homepoker/test/SplitPotScenarioFixture.java`. Wraps the existing `GameManagerTestFixture` patterns but adds:
- Per-seat starting stacks (so a test can specify "stacks 100, 300, 500, 1000")
- Stacked-deck injection via the manager's `Supplier<Deck>` seam
- Returns a `TestableGameManager` already advanced to PRE_FLOP_BETTING with the specified stacks

```java
SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
    .stacks(100, 300, 500, 1000)
    .deck(deck)
    .build();
```

**4. `ShowdownAssert` (test assertion helper)**

Lives in `poker-server/src/test/java/org/homepoker/test/ShowdownAssert.java`. A fluent assertion over the captured `ShowdownResult` event:

```java
ShowdownAssert.from(fixture.savedEvents())
    .hasPotCount(3)
    .pot(0).amount(300).winner(seat1, "Straight, Ace high").and()
    .pot(1).amount(600).winners(seat2, seat3).chopsEvenly().and()
    .pot(2).amount(400).winner(seat3, "Pair of Eights").and()
    .totalAwarded(1300)
    .chipConservation(initialTotalChips);
```

Capabilities:
- `hasPotCount(n)` — exactly n `PotResult` entries in the event
- `pot(i).amount(n)` — pot's total amount
- `pot(i).winner(seat, hand)` — single-winner pot, asserts seat and hand description
- `pot(i).winners(seats...).chopsEvenly()` — multiple winners, equal share, no odd chip
- `pot(i).winners(seats...).oddChipTo(seat)` — multiple winners, asserts who got the extra chip
- `totalAwarded(n)` — sum of all pot amounts equals n
- `chipConservation(initialTotal)` — sum of final player chip counts plus any uncalled-bet returns equals the initial total

## Test Scenarios

8 scenarios in a new test class `SplitPotScenariosTest` in `poker-server/src/test/java/org/homepoker/game/table/`.

### Group A — Baseline structural (no ties, no folds)

**A1. 3 pots, preflop all-ins**
4 players with stacks 100 / 300 / 500 / 1000. All go all-in preflop.
- Pot 1 (main): 400, all 4 eligible → seat 1 wins
- Pot 2 (side): 600, seats 2/3/4 eligible → seat 2 wins
- Pot 3 (side): 400, seats 3/4 eligible → seat 3 wins
- Seat 4's uncalled 500 returned

**A2. 4 pots, preflop all-ins**
5 players: four all-in at 100/300/500/700, one deep stack (2000) calls 700. Distinct winner per pot.
- Pot 1 (main): 500, all 5 eligible
- Pot 2 (side): 800, seats 2/3/4/5 eligible
- Pot 3 (side): 600, seats 3/4/5 eligible
- Pot 4 (side): 400, seats 4/5 eligible
- Deep stack's remaining 1300 stays in their stack

### Group B — Cross-street all-ins

**B1. 3 pots, staggered streets**
4 players: shortest all-in preflop, mid all-in on flop, big all-in on turn, deep stack calls each. Verifies `collectBetsIntoPots()` produces the right pot structure across multiple invocations.

**B2. 3 pots, preflop all-ins + flop all-in**
5 players: 2 all-in preflop (different stacks), 1 all-in on flop, 2 with chips remaining. Tests pot accumulation — a pot formed preflop receives additional contributions during the flop round before being closed.

### Group C — Ties / chops

**C1. 3 pots, main pot tied**
4 all-in players where seats 1 and 2 hold identical-strength hands and chop the main pot, while a separate winner takes each side pot.

**C2. 3 pots, odd-chip distribution**
Designed so a tied pot's amount does not divide evenly. E.g. 3 winners chop a 301-chip pot → 100 / 100 / 101, with the odd chip awarded to the first winner from the dealer's left (matches `evaluatePotWinners()` lines 916-920).

### Group D — Folded contributors

**D1. 3 pots, folded contributor**
Player A bets preflop, folds on flop. Players B and C continue and go all-in. A's contributed chips are in the main pot but A is ineligible. Pot structure forms correctly without A in any eligible-seats list.

**D2. 4 pots, folded contributor + chopped side pot**
Compound: one player contributes-and-folds; two of the remaining all-in players chop a side pot. Stress test for chip conservation when both ineligibility and chops apply in the same hand.

### Coverage matrix

| Scenario | Pots | Cross-street | Tie | Folded contributor |
|---|---|---|---|---|
| A1 | 3 | – | – | – |
| A2 | 4 | – | – | – |
| B1 | 3 | ✓ | – | – |
| B2 | 3 | ✓ | – | – |
| C1 | 3 | – | ✓ | – |
| C2 | 3 | – | ✓ | – |
| D1 | 3 | ✓ | – | ✓ |
| D2 | 4 | ✓ | ✓ | ✓ |

## Test Flow (Per Scenario)

1. Build a stacked `Deck` via `DeckBuilder.holdem(N)`.
2. Build the fixture: `SplitPotScenarioFixture.builder().stacks(...).deck(deck).build()`.
3. Drive the hand using the existing `submitActionAndTick()` / `playAllActionsUntilHandComplete()` helpers from `TexasHoldemTableManagerTest` (extracted to a shared place if needed, or duplicated inline).
4. After `HAND_COMPLETE`, run `ShowdownAssert.from(fixture.savedEvents())` with the expected pot/winner expectations and `chipConservation(initialTotal)`.

## Universal Invariants (asserted on every test)

- Chip conservation: `sum(finalChipCounts) + uncalledReturned == initialTotalChips`
- Pot count matches expectation
- Eligible seats per pot match expectation (folded / ineligible players never appear in any pot's eligible list)
- Exactly one `ShowdownResult` event emitted, with `PotResult` entries summing to the total pot amount
- Each `Winner.amount` equals the actual chip-count delta for that player

## Edge Cases Explicitly Covered

- Uncalled bet returned to deepest stack (A1, A2, B1)
- Two consecutive peel levels with identical eligible seats merged into a single pot (validated by `hasPotCount` in scenarios where stacks would otherwise produce a redundant pot)
- Odd-chip tiebreak deterministic (C2)
- Folded player whose chips remain in pot (D1, D2)
- Tie within a side pot, not the main pot (D2)

## File Inventory

Production:
- `poker-server/src/main/java/org/homepoker/poker/Deck.java` — add stacked-cards constructor
- `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` — replace direct `new Deck()` with a `Supplier<Deck>` field. Default supplier is `Deck::new`. The supplier is set via a constructor parameter (not a setter) so the manager remains immutable post-construction; tests pass an alternate supplier via the existing `TestableGameManager` path.

Test (new):
- `poker-server/src/test/java/org/homepoker/test/DeckBuilder.java`
- `poker-server/src/test/java/org/homepoker/test/SplitPotScenarioFixture.java`
- `poker-server/src/test/java/org/homepoker/test/ShowdownAssert.java`
- `poker-server/src/test/java/org/homepoker/game/table/SplitPotScenariosTest.java`

Test (modified, only if needed):
- `poker-server/src/test/java/org/homepoker/test/GameManagerTestFixture.java` — only if the new fixture needs to share helpers; prefer leaving it untouched

## Risks & Mitigations

- **Risk:** Adding a `Supplier<Deck>` seam touches a hot path. **Mitigation:** Default supplier is `Deck::new`; production behavior unchanged. Verified by all existing `TexasHoldemTableManagerTest` tests continuing to pass.
- **Risk:** Hand strengths chosen for tied scenarios might not actually tie under `BitwisePokerRanker`. **Mitigation:** Each tied scenario must be implemented with a small smoke step that ranks the chosen 7-card combinations directly via `BitwisePokerRanker` to confirm equal rank values before relying on them in the assertion.
- **Risk:** Action sequencing for cross-street scenarios is intricate; small mistakes silently change which round an all-in lands in. **Mitigation:** Each cross-street test asserts the table's `handPhase` at the moment each all-in is submitted, in addition to final state.
