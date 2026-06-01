# Showdown Winning-Hand Cards Design

**Date:** 2026-05-31
**Status:** Approved (pending user spec review)

## Goal

Codify, in the `ShowdownResult` event, exactly which cards make up each winner's best five-card hand, so clients can highlight those cards when displaying the winner. Each winner's `winningCards` is the complete best five-card hand **including kickers** (e.g. for trip Aces with a King/Nine kicker: `A♠ A♥ A♣ K♦ 9♠`), expressed as actual `Card`s (value + suit) so the client can match them unambiguously against the board and the winner's revealed hole cards.

## Why

`ShowdownResult.Winner` currently carries only `seatPosition`, `userId`, `amount`, and a `handDescription` string. The actual cards forming the hand are never recorded. They cannot simply be read off `HandResult` either: `HandResult` holds only a `HandRank` plus up to five `CardValue`s (face values, **no suit, no card identity**), and `BitwisePokerRanker` operates on per-suit bitmaps that discard which concrete `Card` produced each value (see `fifthValue` derivation in `BitwisePokerRanker.isFourOfAKind`, line 120). So the "which cards won" information must be reconstructed from the winner's known cards.

## Out of Scope

- Revealing **losing** players' cards. Only winners' cards (already revealed at showdown) and community cards (public) appear in the event.
- Changing pot evaluation, tie-breaking, or chip distribution logic.
- Changing what `markShowdownCards` reveals (it continues to reveal both of a winner's hole cards).
- WebSocket / REST delivery and client-side highlighting UI.
- The admin debug-view flag — no interaction; winning cards are already-public/revealed cards.

## Approach

Make `HandResult` self-describing: in addition to rank + values, it carries the actual `Card`s that form the hand. The ranker populates them. This keeps a single source of truth (the ranker is the authority on what the best hand is) and benefits future features (hand logs/replays).

Critically, the nine bitwise helper methods (`isFlush`, `isFullHouse`, …) are **left untouched** — the hot-path bitmap math and its existing tests are unchanged. The cards are resolved in a single post-pass at the end of `rankHand`, which already has the original `List<Card>` in scope.

## Architecture

### 1. `HandCardSelector` (new, `org.homepoker.poker`)

A small, package-private, stateless helper — the only new place that knows how to map a ranked result back to concrete cards. Independently unit-testable.

```java
static List<Card> select(List<Card> pool, HandRank rank, List<CardValue> values)
```

- Returns the concrete cards corresponding to `values`, in the same (most-significant-first) order. Size equals `values.size()` (normally 5; fewer for short hands).
- **Algorithm:**
  - **FLUSH / STRAIGHT_FLUSH:** determine the flush suit = the suit with the most cards in `pool` (with ≤7 cards exactly one suit can have ≥5, so this is unambiguous). Restrict to that suit, then pick the card of that suit matching each value in order. This is the case where matching by value alone is wrong — it could pick e.g. `A♠` when the flush is in hearts.
  - **All other ranks** (FOUR_OF_A_KIND, FULL_HOUSE, STRAIGHT, THREE_OF_A_KIND, TWO_PAIR, PAIR, HIGH_CARD): walk `values` in order; for each, consume the first not-yet-used card in `pool` with that value. Consuming cards as we go makes repeated values (pairs/trips/quads) pick distinct suits naturally. Suit is irrelevant to these ranks, so any card of the right value is correct.
- **Wheel (A-2-3-4-5):** `values` is `[5,4,3,2,A]`; the Ace matches by `CardValue.ACE`, so the low-ace straight/straight-flush resolves correctly.
- **Invariant:** every value in a valid `(pool, result)` pair exists in `pool`. If a value is unexpectedly missing, skip it defensively rather than throw (documented).

### 2. `HandResult` (modified, `org.homepoker.poker`)

- Add `private final List<Card> handCards;` with `public List<Card> getHandCards()`.
- Existing two-arg constructor `HandResult(rank, cardValues)` sets `handCards = List.of()` (empty, immutable) — every helper call site is unchanged.
- Add a wither `HandResult withHandCards(List<Card> cards)` returning a copy with the cards attached. `rankHand` uses this once at the end; helpers keep using the two-arg constructor.
- **`equals` / `hashCode` / `compareTo` stay defined on `rank` + `cardValues` only** — `handCards` is descriptive payload, not identity. Hand *strength* (which the showdown tie logic in `evaluatePotWinners`, lines 913/918, and the existing ranker tests rely on) is unaffected. A code comment documents this exclusion.
- Add the `org.homepoker.model.poker.Card` import.

### 3. `BitwisePokerRanker.rankHand` (modified)

After computing `result` (unchanged), resolve and attach the cards:

```java
return result.withHandCards(
    HandCardSelector.select(cards, result.getRank(), result.getCardValues()));
```

The per-rank helper methods are not modified.

### 4. `ShowdownResult.Winner` (modified, `poker-common`)

Add the cards to the record:

```java
public record Winner(int seatPosition, String userId, int amount,
                     String handDescription, List<Card> winningCards) {}
```

Add the `org.homepoker.model.poker.Card` import. `Card` already serializes in events (`CommunityCardsDealt` uses `List<Card>`), so no serialization work is needed.

### 5. Showdown wiring (`TexasHoldemTableManager`, modified)

- **`evaluatePotWinners`** (line 939): pass `shr.result().getHandCards()` into the new `winningCards` field.
- **"Last player standing"** path (everyone else folded, no showdown, line ~870): no hand is evaluated and cards are not revealed, so `winningCards` = `List.of()` (empty).

## Data Flow

```
rankHand(7 cards)
  → HandResult(rank, values)                      // helpers, unchanged
  → HandCardSelector.select(7 cards, rank, values) // new post-pass
  → HandResult.withHandCards(5 cards)
evaluatePotWinners(pot)
  → Winner(..., handDescription, getHandCards())   // 5 concrete cards
transitionFromShowdown
  → ShowdownResult(potResults)                     // broadcast
```

## Security

No new exposure. A winner's `winningCards` is drawn from that winner's own hole cards (revealed at showdown via `markShowdownCards`) plus community cards (public). Losing players are never in the winner list, so their cards never appear. A test asserts that no non-winner seat's cards leak into the event.

## Testing

- **`HandCardSelector` unit tests** — one per `HandRank`, asserting the returned concrete cards. Explicitly cover:
  - FLUSH where a higher card of the same value exists in a non-flush suit (suit disambiguation).
  - STRAIGHT_FLUSH including the A-2-3-4-5 wheel.
  - STRAIGHT where a value is paired across suits (either concrete card is acceptable).
  - Kicker inclusion for quads / trips / two-pair / pair / high-card.
- **Showdown scenario test** (deterministic, stacked deck via existing `DeckBuilder`) — assert a known winner's `winningCards` equals the expected five cards.
- **No-leak test** — assert the `ShowdownResult` contains no losing seat's cards.
- **Regression** — existing `HandResult` / ranker tests pass unchanged (equality/compare semantics preserved).
- **Compile updates** — `ShowdownAssertTest` (and any other direct `Winner` constructions in tests) updated for the new field.
