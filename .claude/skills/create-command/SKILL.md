# Create-Command Skill

## Description

Scaffolds a new game command: the command record, handler wiring, associated events, serialization test, and spec documentation. Asks clarifying questions to determine the command scope and then generates all code following project conventions.

## Instructions

You are creating a new command for the home poker server. Follow this workflow step by step.

### Step 1: Gather Requirements

Ask the user the following questions (use AskUserQuestion or conversational clarification):

1. **Command name** — What should the command be called? (e.g., `KickPlayer`, `SetBlinds`, `MuckCards`). Use PascalCase.
2. **Command scope** — Where should the command be handled?
   - **Game-level**: Handled in `GameManager.applyCommand()`. For commands that affect game-wide state (player registration, game lifecycle, etc.).
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
| `poker-common/src/main/java/org/homepoker/model/event/game/GameMessage.java` | Example game event record |
| `poker-common/src/main/java/org/homepoker/model/event/table/PlayerActed.java` | Example table event record |
| `poker-server/src/main/java/org/homepoker/game/GameManager.java` | Game-level command routing (`applyCommand()` switch) |
| `poker-server/src/main/java/org/homepoker/game/table/TableManager.java` | Common table command routing (`applyCommand()` switch) |
| `poker-server/src/main/java/org/homepoker/game/table/TexasHoldemTableManager.java` | Game-specific table command routing (`applySubcommand()` switch) |
| `poker-common/src/test/java/org/homepoker/model/command/CommandSerializationTest.java` | Serialization test pattern |
| `docs/command-event-spec.md` | Existing command/event spec to update |

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
- The `commandId` is derived automatically from the class name via camelToKebabCase (e.g., `KickPlayer` -> `kick-player`)
- Use Java record — no builders, no extra methods needed

### Step 4: Create Event Records (if any)

Create new Java records in `poker-common/src/main/java/org/homepoker/model/event/`.

Place game events in the `game/` subdirectory. Place table events in the `table/` subdirectory. Place user events in the `user/` subdirectory.

**For game events:**

```java
package org.homepoker.model.event.game;

import java.time.Instant;
import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.GameEvent;

@EventMarker
public record EventName(Instant timestamp, String gameId /*, additional fields */) implements GameEvent {
}
```

**For table events:**

```java
package org.homepoker.model.event.table;

import java.time.Instant;
import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.TableEvent;

@EventMarker
public record EventName(Instant timestamp, String gameId, String tableId /*, additional fields */) implements TableEvent {
}
```

Key conventions:
- Always annotate with `@EventMarker`
- Always include `Instant timestamp` as the first field
- The `eventType` is derived automatically from the class name via camelToKebabCase

### Step 5: Wire the Command Handler

Based on the command scope, add a case to the appropriate switch statement and create the handler method.

**Game-level** — In `GameManager.applyCommand()`:

```java
case CommandName c -> handleCommandName(c, game, gameContext);
```

Handler method pattern:

```java
private void handleCommandName(CommandName command, T game, GameContext gameContext) {
    // 1. Validate the command is allowed in the current game state
    // 2. Validate permissions if admin-only (use securityUtilities)
    // 3. Mutate game state
    // 4. Queue events: gameContext.queueEvent(new SomeEvent(Instant.now(), ...))
    // 5. Throw ValidationException for invalid commands
}
```

**Table-level (common)** — In `TableManager.applyCommand()`:

```java
case CommandName c -> handleCommandName(c, game, table, gameContext);
```

Handler method pattern:

```java
private void handleCommandName(CommandName command, Game<T> game, Table table, GameContext gameContext) {
    // Handle common table command logic
}
```

**Table-level (game-specific)** — In the appropriate subclass (e.g., `TexasHoldemTableManager.applySubcommand()`):

```java
case CommandName c -> handleCommandName(c, game, table, gameContext);
```

Handler method pattern:

```java
private void handleCommandName(CommandName command, Game<T> game, Table table, GameContext gameContext) {
    // Handle game-specific table command logic
}
```

### Step 6: Add Serialization Test

Add a test case to `CommandSerializationTest.java` to verify the command serializes/deserializes correctly through Jackson's polymorphic type handling.

The test should:
1. Create a command instance
2. Serialize to JSON
3. Verify the `commandId` discriminator is present and correct
4. Deserialize back and verify the type
5. Confirm `user` field is excluded from JSON (`@JsonIgnore`)

### Step 7: Update the Command/Event Spec

Update `docs/command-event-spec.md` with the new command and any new events. Follow the existing format in the spec document:

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
- **Events**: `@EventMarker` record. Implements `GameEvent` or `TableEvent`. Fields: `timestamp`, `gameId`, optional `tableId`, plus event-specific fields.
- **Validation**: Throw `ValidationException` with a descriptive message. The game loop catches it and emits a `UserMessage` to the command's user.
- **State mutation**: All on single game loop thread. No synchronization needed inside handlers.
- **Event queueing**: `gameContext.queueEvent(new SomeEvent(Instant.now(), ...))`.
- **No REST endpoints**: Game-time commands are submitted via WebSocket. Only pre-game operations (signup, registration, game management) use REST controllers.
- **Spec update**: Always update `docs/command-event-spec.md` when adding commands or events.

### Checklist

1. [ ] Command record created with `@GameCommandMarker`
2. [ ] Event record(s) created with `@EventMarker` (if applicable)
3. [ ] Switch case added in the appropriate routing method
4. [ ] Handler method implemented with validation and event queueing
5. [ ] Serialization test added
6. [ ] `docs/command-event-spec.md` updated with new command and events
7. [ ] `./gradlew clean build` passes
