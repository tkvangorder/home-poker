---
name: add-event-type
description: Use when adding or changing a game, table, or user event (outbound server → client). Covers the event record, sequence-number plumbing, serialization test, emission site, command-event-spec.md update, and the mandatory hole-card/intent privacy check for any event carrying seat or card data.
---

# Add-Event-Type Skill

Scaffolds a new event on the outbound side. The inbound mirror is `create-command`, which delegates here for any events it introduces.

## Step 1: Pin down the event

1. **Name** — PascalCase, past tense or noun (`BlindPosted`, `HandComplete`, `TableSnapshot`).
2. **Interface / package**
   - `GameEvent` → `model/event/game/` — game-wide stream, sequenced.
   - `TableEvent` → `model/event/table/` — per-table stream, sequenced.
   - `UserEvent` → `model/event/user/` — delivered to one user only, **not** sequenced (no `sequenceNumber`).
   - `TableEvent, UserEvent` together — a table-stream event that is private to one player (see `HoleCardsDealt`).
3. **Fields** — prefer primitives and IDs over embedding mutable aggregates (`Table`, `Seat`, `Player`).
4. **Recipients** — broadcast, single user, or admin-only? If it carries seat/card data, who sees which seats?
5. **Emitted from** — which command handler or transition method queues it?

## Step 2: Read a sibling

| File | Why |
|---|---|
| `poker-common/src/main/java/org/homepoker/model/event/game/GameMessage.java` | Minimal `GameEvent` |
| `poker-common/src/main/java/org/homepoker/model/event/table/PlayerActed.java` | Typical `TableEvent` |
| `poker-common/src/main/java/org/homepoker/model/event/table/HoleCardsDealt.java` | Private per-player table event |
| `poker-common/src/main/java/org/homepoker/model/event/user/TableSnapshot.java` | `UserEvent` carrying a sanitized `Table` |
| `poker-common/src/test/java/org/homepoker/model/event/PokerEventSerializationTest.java` | Round-trip test pattern |

## Step 3: Generate

**Sequenced event (`GameEvent` / `TableEvent`)** — `timestamp` first, `sequenceNumber` second, and override `withSequenceNumber`:

```java
@EventMarker
public record EventName(
    Instant timestamp,
    long sequenceNumber,
    String gameId,
    String tableId,          // TableEvent only
    /* event fields */
) implements TableEvent {
  @Override
  public EventName withSequenceNumber(long sequenceNumber) {
    return new EventName(timestamp, sequenceNumber, gameId, tableId /*, ... */);
  }
}
```

Construct with `sequenceNumber = 0`; the manager stamps the real value at fan-out.

**`UserEvent`** — `timestamp`, `userId`, `gameId`, then fields. No `sequenceNumber`, no `withSequenceNumber`.

Conventions:
- `@EventMarker` is required; it drives Jackson subtype registration (`PokerEvent.pokerEventModule()`). Do **not** add `@JsonTypeName`.
- `eventType` is derived from the class name (`BlindPosted` → `blind-posted`).

Then:
1. **Serialization test** — add a round-trip case to `PokerEventSerializationTest` (serialize, deserialize, `isEqualTo`, and check `sequenceNumber` for sequenced events).
2. **Emission site** — `gameContext.queueEvent(new EventName(Instant.now(), 0, ...))` in the handler/transition.
3. **Listener wiring** — only if a `GameListener` / `UserGameListener` must react specially.
4. **Spec** — add the event to `poker-server/src/main/resources/static/command-event-spec.md` (field table, `eventType`, when it's emitted). Required by CLAUDE.md for every add, rename, or field change, including nested records.

## Step 4: Hole-card safety (MANDATORY if the event carries seat, table, or card data)

- A `Table`/`Seat` sent to a user must go through `TableManager.sanitizeTable(table, userId)`, which strips `cards` and `pendingIntent` from seats the user doesn't own.
- A private per-player payload (like hole cards) must implement `UserEvent` so delivery is scoped to `userId()`.
- A broadcast event must never carry any player's hole cards. The only exception is the admin debug-view warning (see CLAUDE.md).
- Add a test that captures what a **non-owner** user receives and asserts foreign seats have `cards == null` and `pendingIntent == null`. Use the `test-game-scenario` skill.
- Afterwards, consider running the `hole-card-auditor` agent.

## Step 5: Run

```bash
./gradlew :poker-common:test :poker-server:test
```
