# Winning-Hand Cards in ShowdownResult Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record the concrete five cards that make up each winner's best hand in the `ShowdownResult` event, so clients can highlight them.

**Architecture:** `HandResult` becomes self-describing by carrying the actual `Card`s that form the hand (in addition to rank + values). A new `HandCardSelector` resolves those cards from the original 7-card pool; `BitwisePokerRanker.rankHand` attaches them in a single post-pass without touching the bitwise helpers. The showdown code copies `HandResult.getHandCards()` into a new `winningCards` field on `ShowdownResult.Winner`.

**Tech Stack:** Java 25, Spring Boot 4, Gradle (Groovy DSL), JUnit 5, AssertJ. Two modules: `poker-common` (models/events) and `poker-server` (game logic, ranker, tests).

**Spec:** `docs/superpowers/specs/2026-05-31-showdown-winning-hand-cards-design.md`

---

### Task 1: `HandResult` carries the hand's concrete cards

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/poker/HandResult.java`
- Test: `poker-server/src/test/java/org/homepoker/poker/HandResultTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `poker-server/src/test/java/org/homepoker/poker/HandResultTest.java`:

```java
package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.homepoker.lib.poker.PokerUtilities.parseCards;

class HandResultTest {

  @Test
  void handCardsDefaultToEmpty() {
    HandResult result = new HandResult(HandRank.PAIR, List.of(CardValue.ACE, CardValue.ACE, CardValue.KING));
    assertThat(result.getHandCards()).isEmpty();
  }

  @Test
  void withHandCardsAttachesCardsAndPreservesRankAndValues() {
    HandResult base = new HandResult(HandRank.PAIR, List.of(CardValue.ACE, CardValue.ACE, CardValue.KING));
    List<Card> cards = parseCards("As Ah Ks");

    HandResult withCards = base.withHandCards(cards);

    assertThat(withCards.getRank()).isEqualTo(HandRank.PAIR);
    assertThat(withCards.getCardValues()).isEqualTo(base.getCardValues());
    assertThat(withCards.getHandCards()).containsExactlyElementsOf(cards);
  }

  @Test
  void equalityAndCompareIgnoreHandCards() {
    List<CardValue> values = List.of(CardValue.ACE, CardValue.ACE, CardValue.KING);
    HandResult withoutCards = new HandResult(HandRank.PAIR, values);
    HandResult withCards = new HandResult(HandRank.PAIR, values).withHandCards(parseCards("As Ah Ks"));

    assertThat(withCards).isEqualTo(withoutCards);
    assertThat(withCards.hashCode()).isEqualTo(withoutCards.hashCode());
    assertThat(withCards.compareTo(withoutCards)).isZero();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :poker-server:test --tests "org.homepoker.poker.HandResultTest"`
Expected: FAIL — compilation error, `getHandCards()` and `withHandCards(...)` do not exist.

- [ ] **Step 3: Modify `HandResult`**

In `poker-server/src/main/java/org/homepoker/poker/HandResult.java`:

Add the import (after the existing `import org.homepoker.model.poker.CardValue;`):

```java
import org.homepoker.model.poker.Card;
```

Add a field next to the existing fields (after `private final List<CardValue> cardValues;`):

```java
  // Concrete cards that form this hand (value + suit). Descriptive payload only:
  // intentionally NOT part of equals/hashCode/compareTo, which define hand STRENGTH
  // as rank + cardValues. Empty when the result was produced without a card pool.
  private final List<Card> handCards;
```

Replace the existing constructor:

```java
  public HandResult(HandRank rank, List<CardValue> cardValues) {
    Assert.notNull(rank, "The hand rank cannot be null");
    Assert.isTrue(cardValues != null & cardValues.size() <= 5, "You must supply between 1 and 5 card ranks");

    this.rank = rank;
    this.cardValues = cardValues;
  }
```

with two constructors plus a wither and getter:

```java
  public HandResult(HandRank rank, List<CardValue> cardValues) {
    this(rank, cardValues, List.of());
  }

  public HandResult(HandRank rank, List<CardValue> cardValues, List<Card> handCards) {
    Assert.notNull(rank, "The hand rank cannot be null");
    Assert.isTrue(cardValues != null & cardValues.size() <= 5, "You must supply between 1 and 5 card ranks");

    this.rank = rank;
    this.cardValues = cardValues;
    this.handCards = handCards == null ? List.of() : List.copyOf(handCards);
  }

  /**
   * Returns a copy of this result with the concrete cards that form the hand attached.
   * Rank and card values are unchanged, so hand strength (equals/compareTo) is unaffected.
   */
  public HandResult withHandCards(List<Card> handCards) {
    return new HandResult(this.rank, this.cardValues, handCards);
  }

  public List<Card> getHandCards() {
    return handCards;
  }
```

Leave `equals`, `hashCode`, `compareTo`, `getRank`, `getCardValues`, `toString`, and the `LOWEST` constant exactly as they are.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :poker-server:test --tests "org.homepoker.poker.HandResultTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/poker/HandResult.java \
        poker-server/src/test/java/org/homepoker/poker/HandResultTest.java
git commit -m "feat: HandResult carries the concrete cards forming the hand"
```

---

### Task 2: `HandCardSelector` resolves concrete cards from a ranked result

**Files:**
- Create: `poker-server/src/main/java/org/homepoker/poker/HandCardSelector.java`
- Test: `poker-server/src/test/java/org/homepoker/poker/HandCardSelectorTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `poker-server/src/test/java/org/homepoker/poker/HandCardSelectorTest.java`:

```java
package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.homepoker.lib.poker.PokerUtilities.parseCards;
import static org.homepoker.model.poker.CardValue.ACE;
import static org.homepoker.model.poker.CardValue.EIGHT;
import static org.homepoker.model.poker.CardValue.FIVE;
import static org.homepoker.model.poker.CardValue.FOUR;
import static org.homepoker.model.poker.CardValue.KING;
import static org.homepoker.model.poker.CardValue.NINE;
import static org.homepoker.model.poker.CardValue.SEVEN;
import static org.homepoker.model.poker.CardValue.SIX;
import static org.homepoker.model.poker.CardValue.THREE;
import static org.homepoker.model.poker.CardValue.TWO;

class HandCardSelectorTest {

  @Test
  void flushPicksTheFlushSuitNotASameValueCardInAnotherSuit() {
    List<Card> pool = parseCards("Ah Kh 9h 5h 2h As Kd");
    List<CardValue> values = List.of(ACE, KING, NINE, FIVE, TWO);

    List<Card> selected = HandCardSelector.select(pool, HandRank.FLUSH, values);

    // All hearts, in value order — NOT the As/Kd from other suits.
    assertThat(selected).containsExactlyElementsOf(parseCards("Ah Kh 9h 5h 2h"));
  }

  @Test
  void straightFlushWheelResolvesTheLowAce() {
    List<Card> pool = parseCards("Ah 2h 3h 4h 5h Ks Qd");
    List<CardValue> values = List.of(FIVE, FOUR, THREE, TWO, ACE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.STRAIGHT_FLUSH, values);

    assertThat(selected).containsExactlyElementsOf(parseCards("5h 4h 3h 2h Ah"));
  }

  @Test
  void fourOfAKindIncludesTheKicker() {
    List<Card> pool = parseCards("As Ah Ad Ac Kd 9s 2c");
    List<CardValue> values = List.of(ACE, ACE, ACE, ACE, KING);

    List<Card> selected = HandCardSelector.select(pool, HandRank.FOUR_OF_A_KIND, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ad Ac Kd"));
  }

  @Test
  void fullHouseSelectsTripsAndPair() {
    List<Card> pool = parseCards("Ks Kh Kd 2s 2h 9c 5d");
    List<CardValue> values = List.of(KING, KING, KING, TWO, TWO);

    List<Card> selected = HandCardSelector.select(pool, HandRank.FULL_HOUSE, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("Ks Kh Kd 2s 2h"));
  }

  @Test
  void threeOfAKindIncludesTwoKickers() {
    List<Card> pool = parseCards("As Ah Ad Ks 9s 5d 2c");
    List<CardValue> values = List.of(ACE, ACE, ACE, KING, NINE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.THREE_OF_A_KIND, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ad Ks 9s"));
  }

  @Test
  void twoPairIncludesTheKicker() {
    List<Card> pool = parseCards("As Ah Ks Kh 9s 5d 2c");
    List<CardValue> values = List.of(ACE, ACE, KING, KING, NINE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.TWO_PAIR, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ks Kh 9s"));
  }

  @Test
  void pairIncludesThreeKickers() {
    List<Card> pool = parseCards("As Ah Ks 9s 5d 2c 3d");
    List<CardValue> values = List.of(ACE, ACE, KING, NINE, FIVE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.PAIR, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ks 9s 5d"));
  }

  @Test
  void straightWithAPairedValuePicksExactlyFiveCards() {
    // 9-8-7-6-5 straight; the FIVE appears twice (5s, 5h). Either is acceptable.
    List<Card> pool = parseCards("9s 8h 7d 6c 5s 5h 2c");
    List<CardValue> values = List.of(NINE, EIGHT, SEVEN, SIX, FIVE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.STRAIGHT, values);

    assertThat(selected).hasSize(5);
    assertThat(selected).contains(parseCards("9s 8h 7d 6c").toArray(new Card[0]));
    assertThat(selected).filteredOn(c -> c.value() == FIVE).hasSize(1);
  }

  @Test
  void highCardSelectsTheFiveHighestByValue() {
    List<Card> pool = parseCards("As Kh 9d 5s 3c 2h 7s");
    List<CardValue> values = List.of(ACE, KING, NINE, SEVEN, FIVE);

    List<Card> selected = HandCardSelector.select(pool, HandRank.HIGH_CARD, values);

    assertThat(selected).containsExactlyInAnyOrderElementsOf(parseCards("As Kh 9d 7s 5s"));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :poker-server:test --tests "org.homepoker.poker.HandCardSelectorTest"`
Expected: FAIL — compilation error, `HandCardSelector` does not exist.

- [ ] **Step 3: Create `HandCardSelector`**

Create `poker-server/src/main/java/org/homepoker/poker/HandCardSelector.java`:

```java
package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.homepoker.model.poker.CardSuit;
import org.homepoker.model.poker.CardValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the concrete {@link Card}s that make up a ranked hand, given the original pool of
 * cards (the player's hole cards plus the community cards) and the {@link HandResult}'s rank
 * and ordered card values.
 * <p>
 * The bitwise ranker computes a hand's strength from suit bitmaps and discards card identity,
 * so the concrete cards must be reconstructed here. For every rank except a flush, matching by
 * value is sufficient (suit is irrelevant). For a FLUSH / STRAIGHT_FLUSH the values alone are
 * ambiguous — a same-value card can exist in a non-flush suit — so selection is restricted to
 * the suit that actually holds the flush (the suit with the most cards; with at most 7 cards
 * exactly one suit can have 5+).
 */
final class HandCardSelector {

  private HandCardSelector() {
  }

  /**
   * @param pool   the cards that were ranked (hole + community)
   * @param rank   the resulting hand rank
   * @param values the resulting card values, most significant first
   * @return the concrete cards forming the hand, in {@code values} order. Size equals
   *         {@code values.size()} for a valid (pool, result) pair.
   */
  static List<Card> select(List<Card> pool, HandRank rank, List<CardValue> values) {
    List<Card> candidates = (rank == HandRank.FLUSH || rank == HandRank.STRAIGHT_FLUSH)
        ? cardsOfSuit(pool, dominantSuit(pool))
        : new ArrayList<>(pool);
    return consumeByValue(candidates, values);
  }

  /** Walks {@code values} in order, consuming one not-yet-used card of each value. */
  private static List<Card> consumeByValue(List<Card> candidates, List<CardValue> values) {
    List<Card> remaining = new ArrayList<>(candidates);
    List<Card> result = new ArrayList<>(values.size());
    for (CardValue value : values) {
      for (int i = 0; i < remaining.size(); i++) {
        if (remaining.get(i).value() == value) {
          result.add(remaining.remove(i));
          break;
        }
      }
    }
    return result;
  }

  private static List<Card> cardsOfSuit(List<Card> pool, CardSuit suit) {
    List<Card> suited = new ArrayList<>();
    for (Card c : pool) {
      if (c.suit() == suit) {
        suited.add(c);
      }
    }
    return suited;
  }

  private static CardSuit dominantSuit(List<Card> pool) {
    int[] counts = new int[CardSuit.values().length];
    for (Card c : pool) {
      counts[c.suit().ordinal()]++;
    }
    int best = 0;
    for (int i = 1; i < counts.length; i++) {
      if (counts[i] > counts[best]) {
        best = i;
      }
    }
    return CardSuit.values()[best];
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :poker-server:test --tests "org.homepoker.poker.HandCardSelectorTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/poker/HandCardSelector.java \
        poker-server/src/test/java/org/homepoker/poker/HandCardSelectorTest.java
git commit -m "feat: add HandCardSelector to resolve concrete winning-hand cards"
```

---

### Task 3: `BitwisePokerRanker.rankHand` attaches the concrete cards

**Files:**
- Modify: `poker-server/src/main/java/org/homepoker/poker/BitwisePokerRanker.java:66`
- Test: `poker-server/src/test/java/org/homepoker/poker/BitwisePokerRankerHandCardsTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `poker-server/src/test/java/org/homepoker/poker/BitwisePokerRankerHandCardsTest.java`:

```java
package org.homepoker.poker;

import org.homepoker.model.poker.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.homepoker.lib.poker.PokerUtilities.parseCards;
import static org.homepoker.model.poker.CardValue.FIVE;

class BitwisePokerRankerHandCardsTest {

  private final BitwisePokerRanker ranker = new BitwisePokerRanker();

  @Test
  void rankHandAttachesFlushCardsFromTheCorrectSuit() {
    List<Card> pool = parseCards("Ah Kh 9h 5h 2h As Kd");

    HandResult result = ranker.rankHand(pool);

    assertThat(result.getRank()).isEqualTo(HandRank.FLUSH);
    assertThat(result.getHandCards()).containsExactlyInAnyOrderElementsOf(parseCards("Ah Kh 9h 5h 2h"));
  }

  @Test
  void rankHandAttachesTripsAndKickers() {
    List<Card> pool = parseCards("As Ah Ad Ks 9s 5d 2c");

    HandResult result = ranker.rankHand(pool);

    assertThat(result.getRank()).isEqualTo(HandRank.THREE_OF_A_KIND);
    assertThat(result.getHandCards()).containsExactlyInAnyOrderElementsOf(parseCards("As Ah Ad Ks 9s"));
  }

  @Test
  void rankHandAttachesWheelStraightCards() {
    List<Card> pool = parseCards("Ah 2c 3d 4s 5h Ks Qd");

    HandResult result = ranker.rankHand(pool);

    assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT);
    assertThat(result.getHandCards()).hasSize(5);
    assertThat(result.getHandCards()).contains(parseCards("2c 3d 4s 5h").toArray(new Card[0]));
    assertThat(result.getHandCards()).filteredOn(c -> c.value() == FIVE).hasSize(1);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :poker-server:test --tests "org.homepoker.poker.BitwisePokerRankerHandCardsTest"`
Expected: FAIL — `getHandCards()` is empty (rankHand does not attach cards yet), so the assertions on contents fail.

- [ ] **Step 3: Modify `rankHand`**

In `poker-server/src/main/java/org/homepoker/poker/BitwisePokerRanker.java`, replace the final `return result;` (line 66) at the end of `rankHand`:

```java
    return result;
```

with:

```java
    return result.withHandCards(
        HandCardSelector.select(cards, result.getRank(), result.getCardValues()));
```

Do not modify any of the `isXxx` helper methods.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :poker-server:test --tests "org.homepoker.poker.BitwisePokerRankerHandCardsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the existing ranker tests to confirm no regression**

Run: `./gradlew :poker-server:test --tests "org.homepoker.poker.*"`
Expected: PASS — existing ranker/HandResult tests are unaffected (equality is still rank + values).

- [ ] **Step 6: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/poker/BitwisePokerRanker.java \
        poker-server/src/test/java/org/homepoker/poker/BitwisePokerRankerHandCardsTest.java
git commit -m "feat: attach concrete winning-hand cards in rankHand"
```

---

### Task 4: Add `winningCards` to `ShowdownResult.Winner` and wire the showdown

**Files:**
- Modify: `poker-common/src/main/java/org/homepoker/model/event/table/ShowdownResult.java`
- Modify: `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` (lines ~870 and ~939)
- Modify: `poker-server/src/test/java/org/homepoker/test/ShowdownAssertTest.java` (Winner constructions, lines 20, 22, 38, 39, 50, 51, 52, 62)

This is a structural change. The gate is: the full build compiles and the existing suite stays green. The behavioral assertion lands in Task 6.

- [ ] **Step 1: Add the field to the `Winner` record**

In `poker-common/src/main/java/org/homepoker/model/event/table/ShowdownResult.java`, add the import after `import org.homepoker.model.event.TableEvent;`:

```java
import org.homepoker.model.poker.Card;
```

Replace the `Winner` record:

```java
  /**
   * A winner of a pot or portion of a pot.
   * @param seatPosition The seat position of the winner
   * @param userId The user ID of the winner
   * @param amount The chips won
   * @param handDescription A description of the winning hand (e.g., "Full House, Aces over Kings")
   */
  public record Winner(int seatPosition, String userId, int amount, String handDescription) {
  }
```

with:

```java
  /**
   * A winner of a pot or portion of a pot.
   * @param seatPosition The seat position of the winner
   * @param userId The user ID of the winner
   * @param amount The chips won
   * @param handDescription A description of the winning hand (e.g., "Full House, Aces over Kings")
   * @param winningCards The concrete cards (value + suit) that make up the winner's best hand,
   *                     so clients can highlight them. Empty when the hand was won without a
   *                     showdown (e.g., everyone else folded).
   */
  public record Winner(int seatPosition, String userId, int amount, String handDescription,
                       List<Card> winningCards) {
  }
```

(`java.util.List` is already imported in this file.)

- [ ] **Step 2: Wire the showdown evaluation**

In `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java`, in `evaluatePotWinners` (around line 939), replace:

```java
      winners.add(new ShowdownResult.Winner(shr.position(), player.userId(), amount, handDesc));
```

with:

```java
      winners.add(new ShowdownResult.Winner(
          shr.position(), player.userId(), amount, handDesc, shr.result().getHandCards()));
```

- [ ] **Step 3: Wire the "last player standing" path**

In the same file (around line 870), replace:

```java
          List.of(new ShowdownResult.Winner(winnerPosition, winner.userId(), pot.amount(), "Last player standing"))));
```

with:

```java
          List.of(new ShowdownResult.Winner(
              winnerPosition, winner.userId(), pot.amount(), "Last player standing", List.of()))));
```

- [ ] **Step 4: Fix the test constructions in `ShowdownAssertTest`**

In `poker-server/src/test/java/org/homepoker/test/ShowdownAssertTest.java`, add `, List.of()` as the final argument to every `new ShowdownResult.Winner(...)`. The seven constructions become:

```java
                List.of(new ShowdownResult.Winner(1, "user-1", 400, "Pair of Aces", List.of()))),
```
```java
                List.of(new ShowdownResult.Winner(2, "user-2", 600, "Pair of Kings", List.of())))
```
```java
            new ShowdownResult.Winner(1, "u1", 300, "Two Pair", List.of()),
            new ShowdownResult.Winner(2, "u2", 300, "Two Pair", List.of())))));
```
```java
            new ShowdownResult.Winner(1, "u1", 101, "Pair", List.of()),
            new ShowdownResult.Winner(2, "u2", 100, "Pair", List.of()),
            new ShowdownResult.Winner(3, "u3", 100, "Pair", List.of())))));
```
```java
            List.of(new ShowdownResult.Winner(1, "u1", 100, "x", List.of())))));
```

- [ ] **Step 5: Build and run the full server test suite**

Run: `./gradlew :poker-server:test`
Expected: PASS — everything compiles and all existing tests are green (including `SplitPotScenariosTest` and `TexasHoldemTableManagerTest`, which do not assert `winningCards`).

- [ ] **Step 6: Commit**

```bash
git add poker-common/src/main/java/org/homepoker/model/event/table/ShowdownResult.java \
        poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java \
        poker-server/src/test/java/org/homepoker/test/ShowdownAssertTest.java
git commit -m "feat: add winningCards to ShowdownResult.Winner and populate at showdown"
```

---

### Task 5: Add a `winningCards` fluent assertion to `ShowdownAssert`

**Files:**
- Modify: `poker-server/src/test/java/org/homepoker/test/ShowdownAssert.java`
- Test: `poker-server/src/test/java/org/homepoker/test/ShowdownAssertTest.java`

- [ ] **Step 1: Write the failing self-test**

In `poker-server/src/test/java/org/homepoker/test/ShowdownAssertTest.java`, add this import near the top (the existing `assertThatThrownBy` static import already covers the negative case):

```java
import org.homepoker.lib.poker.PokerUtilities;
```

Add two test methods inside the class:

```java
  @Test
  void winningCardsMatchesRegardlessOfOrder() {
    ShowdownResult event = new ShowdownResult(
        Instant.now(), 1L, "g", "t",
        List.of(new ShowdownResult.PotResult(0, 100,
            List.of(new ShowdownResult.Winner(1, "u1", 100, "THREE_OF_A_KIND",
                PokerUtilities.parseCards("As Ah Ad Ks 9s"))))));

    ShowdownAssert.from(List.of(event))
        .pot(0).winner(1, "THREE_OF_A_KIND").winningCards(1, "9s Ks As Ah Ad");
  }

  @Test
  void winningCardsFailsWhenCardsDiffer() {
    ShowdownResult event = new ShowdownResult(
        Instant.now(), 1L, "g", "t",
        List.of(new ShowdownResult.PotResult(0, 100,
            List.of(new ShowdownResult.Winner(1, "u1", 100, "PAIR",
                PokerUtilities.parseCards("As Ah Ks 9s 5d"))))));

    assertThatThrownBy(() ->
        ShowdownAssert.from(List.of(event)).pot(0).winningCards(1, "As Ah Ks 9s 2c"))
        .isInstanceOf(AssertionError.class);
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :poker-server:test --tests "org.homepoker.test.ShowdownAssertTest"`
Expected: FAIL — compilation error, `winningCards(int, String)` does not exist on `PotAssert`.

- [ ] **Step 3: Add the assertion to `PotAssert`**

In `poker-server/src/test/java/org/homepoker/test/ShowdownAssert.java`, add the import after `import org.homepoker.model.event.table.ShowdownResult;`:

```java
import org.homepoker.lib.poker.PokerUtilities;
import org.homepoker.model.poker.Card;
```

Inside the `PotAssert` class, add this method (e.g., after `winner(...)`):

```java
    /**
     * Asserts the winning cards recorded for the given seat match {@code expectedCards}
     * (a space-separated card string like {@code "As Ah Ad Ks 9s"}), order-independent.
     */
    public PotAssert winningCards(int seatPosition, String expectedCards) {
      ShowdownResult.Winner w = pot.winners().stream()
          .filter(x -> x.seatPosition() == seatPosition)
          .findFirst()
          .orElse(null);
      assertThat(w)
          .as("pot[%d] has a winner at seat %d", index, seatPosition)
          .isNotNull();
      List<Card> expected = PokerUtilities.parseCards(expectedCards);
      assertThat(w.winningCards())
          .as("pot[%d] seat %d winning cards", index, seatPosition)
          .containsExactlyInAnyOrderElementsOf(expected);
      return this;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :poker-server:test --tests "org.homepoker.test.ShowdownAssertTest"`
Expected: PASS (all methods, including the two new ones).

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/test/ShowdownAssert.java \
        poker-server/src/test/java/org/homepoker/test/ShowdownAssertTest.java
git commit -m "test: add winningCards fluent assertion to ShowdownAssert"
```

---

### Task 6: End-to-end scenario — winningCards populated for a known hand

**Files:**
- Test: `poker-server/src/test/java/org/homepoker/game/table/ShowdownWinningCardsTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `poker-server/src/test/java/org/homepoker/game/table/ShowdownWinningCardsTest.java`:

```java
package org.homepoker.game.table;

import org.homepoker.model.command.PlayerActionCommand;
import org.homepoker.model.game.HandPhase;
import org.homepoker.model.game.PlayerAction;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;
import org.homepoker.poker.Deck;
import org.homepoker.test.DeckBuilder;
import org.homepoker.test.ShowdownAssert;
import org.homepoker.test.SplitPotScenarioFixture;
import org.junit.jupiter.api.Test;

/**
 * End-to-end verification that {@link org.homepoker.model.event.table.ShowdownResult} records
 * the concrete cards forming each winner's best hand.
 */
class ShowdownWinningCardsTest {

  @Test
  void recordsTheFiveCardsOfTheWinningHand() {
    // Heads-up. Seat 1 wins with trip Aces (As + Ah Ad on board), kickers Ks (hole) and 9s (board).
    // Seat 2 (7c 2d) only pairs the aces and loses.
    Deck deck = DeckBuilder.holdem(2)
        .holeCards(1, "As Ks")
        .holeCards(2, "7c 2d")
        .flop("Ah Ad 9s").turn("5d").river("3c")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(1000, 1000)
        .deck(deck)
        .build();

    runUntilHandComplete(fixture);

    ShowdownAssert.from(fixture.savedEvents())
        .hasPotCount(1)
        .pot(0).winner(1, "THREE_OF_A_KIND").winningCards(1, "As Ah Ad Ks 9s");
  }

  // Drives the hand to completion by auto-checking/calling for any player to act.
  static void runUntilHandComplete(SplitPotScenarioFixture fixture) {
    int safety = 100;
    while (safety-- > 0) {
      Table table = fixture.table();
      if (table.handPhase() == HandPhase.HAND_COMPLETE) return;
      if (table.actionPosition() == null) {
        fixture.tick();
        continue;
      }
      Seat seat = table.seatAt(table.actionPosition());
      if (seat.status() != Seat.Status.ACTIVE || seat.isAllIn()) {
        fixture.tick();
        continue;
      }
      PlayerAction action = (seat.currentBetAmount() >= table.currentBet())
          ? new PlayerAction.Check()
          : new PlayerAction.Call(0);
      fixture.submitCommand(new PlayerActionCommand(
          fixture.game().id(), fixture.tableId(), seat.player().user(), action));
      fixture.tick();
    }
    throw new AssertionError("Hand did not complete within safety budget");
  }
}
```

- [ ] **Step 2: Run test to verify it fails (against a known-bad baseline)**

Temporarily confirm the assertion is meaningful: this test should PASS now that Tasks 1–5 are in place. To prove it is not a no-op, you may briefly change the expected string to `"As Ah Ad Ks 5d"` and confirm it FAILS, then change it back to `"As Ah Ad Ks 9s"`.

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.table.ShowdownWinningCardsTest"`
Expected (with the wrong string): FAIL — winning cards contain `9s`, not `5d`.

- [ ] **Step 3: Confirm the correct assertion passes**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.table.ShowdownWinningCardsTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/game/table/ShowdownWinningCardsTest.java
git commit -m "test: end-to-end ShowdownResult records the winning hand cards"
```

---

### Task 7: Security — losing players' cards never leak into the event

**Files:**
- Modify: `poker-server/src/test/java/org/homepoker/game/table/ShowdownWinningCardsTest.java`

- [ ] **Step 1: Write the failing test**

Add the following imports to `ShowdownWinningCardsTest.java`:

```java
import org.homepoker.lib.poker.PokerUtilities;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.table.ShowdownResult;
import org.homepoker.model.poker.Card;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
```

Add this test method to the class:

```java
  @Test
  void losingPlayersCardsDoNotLeakIntoTheEvent() {
    // Same hand as above. Seat 2's hole cards (7c, 2d) are the loser's and must not appear
    // in any winner's winningCards (which only ever contains the winner's cards + the board).
    Deck deck = DeckBuilder.holdem(2)
        .holeCards(1, "As Ks")
        .holeCards(2, "7c 2d")
        .flop("Ah Ad 9s").turn("5d").river("3c")
        .build();

    SplitPotScenarioFixture fixture = SplitPotScenarioFixture.builder()
        .stacks(1000, 1000)
        .deck(deck)
        .build();

    runUntilHandComplete(fixture);

    List<Card> loserHoleCards = PokerUtilities.parseCards("7c 2d");
    List<Card> allWinningCards = fixture.savedEvents().stream()
        .filter(e -> e instanceof ShowdownResult)
        .map(e -> (ShowdownResult) e)
        .flatMap(s -> s.potResults().stream())
        .flatMap(p -> p.winners().stream())
        .flatMap(w -> w.winningCards().stream())
        .toList();

    assertThat(allWinningCards)
        .as("loser's hole cards must not appear in any winner's winningCards")
        .doesNotContainAnyElementsOf(loserHoleCards);
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :poker-server:test --tests "org.homepoker.game.table.ShowdownWinningCardsTest"`
Expected: PASS (both methods). The loser's `7c`/`2d` are absent from the winner's cards.

- [ ] **Step 3: Run the full build to confirm everything is green**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL — all modules compile and all tests pass.

- [ ] **Step 4: Commit**

```bash
git add poker-server/src/test/java/org/homepoker/game/table/ShowdownWinningCardsTest.java
git commit -m "test: assert losing players' cards never leak into ShowdownResult"
```

---

## Notes for the implementer

- **Toolset gotchas:** Build files are Groovy DSL (`build.gradle`). Run tests with `./gradlew :poker-server:test --tests "<fully.qualified.ClassName>"`.
- **Card string syntax:** Two chars — value (`A K Q J T 9 8 7 6 5 4 3 2`) then suit (`s h d c`). `PokerUtilities.parseCards("As Ah Ad")` parses a space-separated list; `DeckBuilder` uses the same syntax.
- **Why `containsExactlyInAnyOrder` in most card assertions:** the selector returns cards in value order, but which suit fills a duplicated value (e.g., two 5s in a straight) is an implementation detail, so order/identity within a value is not asserted beyond "exactly five, one per value."
- **Do not** modify the `isXxx` helper methods in `BitwisePokerRanker` or the `equals`/`hashCode`/`compareTo` of `HandResult` — hand strength must remain rank + values only.
