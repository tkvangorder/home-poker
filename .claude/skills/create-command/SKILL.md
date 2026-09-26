---
name: create-command
description: Use when adding or changing a client→server game command (game-level or table-level). Covers the command record, handler routing in GameManager/TableManager, serialization test, command-event-spec.md update, and delegates any new events to add-event-type.
---

# Create-Command Skill

You are creating a new command for the home poker server. Follow this workflow step by step.

### Step 1: Gather Requirements

Ask the user the following questions (use AskUserQuestion or conversational clarification):

1. **Command name** — What should the command be called? (e.g., `KickPlayer`, `SetBlinds`, `MuckCards`). Use PascalCase.
2. **Command scope** — Where should the command be handled?
   - **Game-level**: Handled in `GameManager.applyCommand()` (or `CashGameManager.applyGameSpecificCommand()` for cash-only). For commands that affect game-wide state (player registration, game lifecycle, etc.).
   - **Table-level (common)**: Handled in `TableManager.applyCommand()`. For commands shared across all table/game types.
   - **Table-level (game-specific)**: Handled in a specific `TableManager` subclass (e.g., `TexasHoldemTableManager.applySubcommand()`). For commands tied to a particular game variant.
3. **Command fields** — What data does the command carry beyond `gameId` and `user`? (e.g., `int amount`, `PlayerAction action`). For table-level commands, `tableId` is included automatically.
4. **Events emitted** — Does this command emit any events? If so, what are their names and fields?
5. **Admin-only?** — Is this an admin-only command that requires permission checks?

### Step 2: Read Key Files

Before writing any code, read these files to understand current patterns:

| File | Purpose |
|---|---|
| `poker-common/src/main/java/org/homepoker/model/command/GameCommand.java` | Command interface with `@GameCommandMarker` scanning and `@JsonIgnore` on `user` |
| `poker-common/src/main/java/org/homepoker/model/command/TableCommand.java` | Table command sub-interface (adds `tableId()`) |
| `poker-common/src/main/java/org/homepoker/model/command/EndGame.java` | Example game-level command record |
| `poker-common/src/main/java/org/homepoker/model/command/PlayerActionCommand.java` | Example table-level command record |
| `poker-server/src/main/java/org/homepoker/game/GameManager.java` | Game-level command routing (`applyCommand()` switch) |
| `poker-server/src/main/java/org/homepoker/game/cash/CashGameManager.java` | Cash-only routing (`applyGameSpecificCommand()`) |
| `poker-server/src/main/java/org/homepoker/game/table/TableManager.java` | Common table command routing (`applyCommand()` switch) |
| `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` | Game-specific table command routing (`applySubcommand()` switch) |
| `poker-common/src/test/java/org/homepoker/model/command/CommandSerializationTest.java` | Serialization test pattern |
| `poker-server/src/main/resources/static/command-event-spec.md` | Existing command/event spec to update |

### Step 3: Create the Command Record

Create a new Java record in `poker-common/src/main/java/org/homepoker/model/command/`.

**For game-level commands:**

```java
package org.homepoker.model.command;

import org.homepoker.model.user.User;

@GameCommandMarker
public record CommandName(String gameId, User user /*, additional fields */) implements GameCommand {
}
```

**For table-level commands:**

```java
package org.homepoker.model.command;

import org.homepoker.model.user.User;

@GameCommandMarker
public record CommandName(String gameId, String tableId, User user /*, additional fields */) implements TableCommand {
}
```

Key conventions:
- Always annotate with `@GameCommandMarker` — enables automatic Jackson polymorphic registration
- The `user` field gets `@JsonIgnore` via the `GameCommand` interface default — it is injected server-side, NOT serialized
- The `commandId` is derived automatically from the class name via camelToKabobCase (e.g., `KickPlayer` -> `kick-player`)
- Use Java record — no builders, no extra methods needed

### Step 4: Create Event Records (if any)

For each new event, follow the `add-event-type` skill. It covers the record shape (`timestamp`, `sequenceNumber`, `withSequenceNumber`), the serialization test, the spec entry, and the mandatory hole-card privacy check.

### Step 5: Wire the Command Handler

`GameManager.applyCommand()` routes every `TableCommand` to the matching `TableManager` by `tableId`. Everything else goes through its own switch. Add a case in the right place:

| Scope | Switch to extend | Handler signature |
|---|---|---|
| Game-level (all game types) | `GameManager.applyCommand()` | `private void kickPlayer(KickPlayer c, T game, GameContext gameContext)` |
| Game-level (cash only) | `CashGameManager.applyGameSpecificCommand()` | `private void kickPlayer(KickPlayer c, CashGame game, GameContext gameContext)` |
| Table-level (common) | `TableManager.applyCommand()` | Uses the manager's own `table` field |
| Table-level (Hold'em) | `TexasHoldemTableManager.applySubcommand()` | `private void applyX(X c, Game<T> game, GameContext gameContext)` |

```java
case KickPlayer c -> kickPlayer(c, game, gameContext);
```

Each handler:
1. Validates that the command is allowed in the current `GameStatus` / `HandPhase`
2. Checks admin permission if required (`securityUtilities()`)
3. Mutates state (single game-loop thread, so no locking)
4. Queues events: `gameContext.queueEvent(new SomeEvent(Instant.now(), 0, ...))`
5. Throws `ValidationException` for invalid input. The tick loop turns it into a `UserMessage` to the sender.

### Step 6: Add Serialization Test and a Behavior Test

Add a test case to `CommandSerializationTest.java` to verify the command serializes/deserializes correctly through Jackson's polymorphic type handling.

The test should:
1. Create a command instance
2. Serialize to JSON
3. Verify the `commandId` discriminator is present and correct
4. Deserialize back and verify the type
5. Confirm `user` field is excluded from JSON (`@JsonIgnore`)

Also add a game-loop test for the handler (valid and rejected cases) using the `test-game-scenario` skill.

### Step 7: Update the Command/Event Spec

Update `poker-server/src/main/resources/static/command-event-spec.md` with the new command and any new events. Follow the existing format in the spec document:

- Add the command entry in the appropriate section (Game-Level Commands or Table-Level Commands)
- Include: field table, commandId, accepted states/phases, validation notes
- Add any new event entries in the appropriate section
- Include: field table, eventType, description of when the event is emitted

### Step 8: Build and Verify

Run the build to ensure everything compiles and tests pass:

```bash
./gradlew clean build
```

### Patterns Summary

- **Commands**: `@GameCommandMarker` record. Implements `GameCommand` (game-level) or `TableCommand` (table-level). Fields: `gameId`, `user`, optional `tableId`, plus command-specific fields.
- **Events**: see `add-event-type`.
- **Validation**: Throw `ValidationException` with a descriptive message. The game loop catches it and emits a `UserMessage` to the command's user.
- **State mutation**: All on single game loop thread. No synchronization needed inside handlers.
- **Event queueing**: `gameContext.queueEvent(new SomeEvent(Instant.now(), 0, ...))`. The `0` is the sequence number, stamped later at fan-out.
- **No REST endpoints**: Game-time commands are submitted via WebSocket. Only pre-game operations (signup, registration, game management) use REST controllers.
- **Spec update**: Always update `poker-server/src/main/resources/static/command-event-spec.md` when adding commands or events.

### Checklist

1. [ ] Command record created with `@GameCommandMarker`
2. [ ] Event record(s) created via `add-event-type` (if applicable)
3. [ ] Switch case added in the appropriate routing method
4. [ ] Handler method implemented with validation and event queueing
5. [ ] Serialization test added
6. [ ] `poker-server/src/main/resources/static/command-event-spec.md` updated with new command and events
7. [ ] `./gradlew clean build` passes
