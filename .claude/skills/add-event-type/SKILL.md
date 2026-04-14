# Add-Event-Type Skill

## Description

Scaffolds a new game event: the event record, serialization test, listener wiring, and — critically — the hole-card/intent stripping check for any event that carries seat data. Mirror of `create-command`, but for the outbound (event) side.

## Instructions

### Step 1: Gather requirements

1. **Event name** — PascalCase, ends in past tense (e.g. `BlindsPosted`, `PlayerBustedOut`, `AdminViewEnabled`).
2. **Scope** — `GameEvent`, `TableEvent`, `UserEvent`, or a sub-package (`game/`, `table/`, `user/`)?
3. **Fields** — what does it carry? (Prefer primitives + IDs; avoid embedding mutable aggregates.)
4. **Recipients** — broadcast to all users, single user, or admin-only? If it carries seat data, who sees which seats?
5. **Emitted from** — which `applyCommand` / transition method queues this event?

### Step 2: Read key files

| File | Purpose |
|---|---|
| `poker-common/src/main/java/org/homepoker/model/event/EventMarker.java` | Marker interface + scanning |
| `poker-common/src/main/java/org/homepoker/model/event/GameEvent.java` / `TableEvent.java` / `UserEvent.java` | Pick the correct parent |
| An existing sibling event in `event/game/`, `event/table/`, or `event/user/` | Mirror its shape |
| `poker-server/src/main/java/org/homepoker/game/GameContext.java` | `queueEvent()` entry point |
| A matching serialization test | Mirror it for the new event |

### Step 3: Generate

1. **Event record** — Java `record` implementing the correct marker interface. Include `@JsonTypeName` if the project uses it for polymorphic deserialization.
2. **Serialization round-trip test** — serialize via Jackson, deserialize, assert equality. Every new event gets one.
3. **Emission site** — queue the event via `gameContext.queueEvent(...)` from the appropriate handler.
4. **Listener wiring** — if any `GameListener` / `UserGameListener` needs to react (e.g., WebSocket push), add the case.

### Step 4: Hole-card safety check (MANDATORY if event carries seat/table data)

If the event payload references `Seat`, `Table`, or any card-bearing data:
- Per-user scoping must strip `cards` and `pendingIntent` from seats not owned by the recipient.
- Add a dedicated test: build a scenario, capture events for a non-owner user, assert foreign-seat `cards == null` and `pendingIntent == null`.
- If the event is broadcast (not per-user), it MUST NOT carry any player's hole cards at all — unless it is the explicit admin-debug-view warning event.

### Step 5: Run

```bash
./gradlew :poker-common:test :poker-server:test
```