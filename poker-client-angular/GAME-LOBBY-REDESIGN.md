# Game Lobby UI Redesign

## Context

The current game experience is split across two disconnected pages: a lobby at `/game/:gameId` (leaderboard, tables list, messages) and a separate table view at `/game/:gameId/table/:tableId`. When the game becomes ACTIVE, the user must manually click a table and navigate away, losing sight of the leaderboard and messages. The goal is to unify these into a single adaptive view where:

1. The leaderboard is always visible
2. The table view appears inline when the game is active (on the user's assigned table)
3. On wider screens, users can observe other tables

## Key Decision: Single Route, No Child Routes

Keep `GameLobbyComponent` as the sole route at `/game/:gameId`. Embed the table view conditionally within it. Remove the unused `/game` route and `GamePageComponent`.

**Why:** The lobby already owns the WebSocket connection and all game state. The table is a panel within the lobby, not a separate page.

## Layout Design

### Mode 1: Pre-Game (SCHEDULED, SEATING)

```
+----------------------------------------------------------+
| Header: Game Name | Status Badge | Connection | Controls |
+----------------------------------------------------------+
|  Leaderboard (2/3 width)          |   Messages (1/3)     |
|  - Player list with chip counts   |   - Event log        |
|  - Buy-in controls                |                      |
+----------------------------------------------------------+
```

No tables column — it's empty in pre-game anyway.

### Mode 2: Active Game (ACTIVE, BALANCING, PAUSED)

**Desktop (lg+, 1024px+):**
```
+----------------------------------------------------------+
| Header: Game Name | Status Badge | Connection | Controls |
+----------------------------------------------------------+
| Sidebar (w-80)            |  Table View (flex-1)         |
| +----------------------+  |  +------------------------+  |
| | Tab: Leaderboard     |  |  | CashTableComponent     |  |
| | Tab: Messages        |  |  | (your assigned table)  |  |
| +----------------------+  |  +------------------------+  |
| | [active tab content] |  |  | Table tabs (xl+ only)  |  |
| |                      |  |  | My Table | Table 2 |...|  |
| +----------------------+  |  +------------------------+  |
+----------------------------------------------------------+
```

**Mobile (< lg):**
```
+----------------------------------+
| Header (compact)                 |
+----------------------------------+
| Table View (full width/height)   |
| CashTableComponent               |
+----------------------------------+
| Bottom nav (fixed)               |
| [Table] [Leaderboard] [Messages] |
+----------------------------------+
```

### Mode 3: Completed

Full-width final leaderboard. No table view.

## Component Structure

### New Components

1. **`LeaderboardPanelComponent`** — `src/app/game-lobby/leaderboard-panel/`
   - Extract from current leaderboard column in `game-lobby.component.html`
   - Inputs: `players: PlayerState[]`, `currentUserId: string`
   - Contains buy-in controls, highlights current user's row

2. **`MessagesPanelComponent`** — `src/app/game-lobby/messages-panel/`
   - Extract from current messages column
   - Inputs: `messages: (GameMessageEvent | UserMessageEvent)[]`

3. **`TableViewComponent`** — `src/app/game-lobby/table-view/`
   - Wraps `CashTableComponent` with the game background styling
   - Inputs: `tableState: TableState`, `players: PlayerState[]`
   - For the rough design, just embeds `CashTableComponent` as-is (hardcoded data)

### Modified Components

4. **`GameLobbyComponent`** — restructured template with two-mode layout
   - Add `myTableId$` observable (finds current user's assigned table from player state)
   - Add `selectedTableId` property for multi-table tab switching
   - Add `activeTab` for mobile panel switching
   - Import new sub-components

### Removed

5. **`GamePageComponent`** — deprecated, remove from routes
6. **`/game` route** — remove (no gameId, no guard, serves no purpose)

## State: Finding the User's Table

```typescript
myTableId$ = combineLatest([this.players$, ...]).pipe(
  map(([players, user]) => players.get(user.loginId)?.tableId ?? null),
  distinctUntilChanged()
);
```

When `myTableId$` emits non-null and status is ACTIVE, the table view renders automatically.

## Status-Driven Transitions

| Status | View |
|---|---|
| SCHEDULED | Pre-game: leaderboard + messages, "Game starts at [time]" |
| SEATING | Pre-game: buy-in controls active, players appearing |
| ACTIVE | Active: table view appears, sidebar with leaderboard/messages |
| BALANCING | Active: "Rebalancing..." overlay on table |
| PAUSED | Active: "Game Paused" overlay on table |
| COMPLETED | Summary: full-width final leaderboard |

## Route Changes

```typescript
// Remove:
{ path: 'game', component: GamePageComponent }

// Keep (unchanged):
{ path: 'game/:gameId', component: GameLobbyComponent, canActivate: [authenticationGuard] }
```

Replace `navigateToTable()` with `selectTable(tableId)` (sets local property, no navigation).

## Implementation Phases

### Phase 1: Extract sub-components and restructure layout
1. Create `LeaderboardPanelComponent` (extract from game-lobby template)
2. Create `MessagesPanelComponent` (extract from game-lobby template)
3. Create `TableViewComponent` (wraps CashTableComponent with background)
4. Rewrite `GameLobbyComponent` template with two-mode layout
5. Remove `/game` route and `GamePageComponent`

### Phase 2: Wire up state-driven table display
1. Add `myTableId$` computed observable
2. Pass table state into `TableViewComponent`
3. Replace `navigateToTable()` with `selectTable(tableId)`

### Phase 3: Responsive layout
1. Mobile bottom nav with DaisyUI `btm-nav`
2. `activeTab` state for mobile panel switching
3. Tailwind `hidden lg:block` / `lg:hidden` for layout toggling

### Phase 4: Multi-table observation (nice-to-have)
1. Table tab bar above `TableViewComponent` (xl+ only)
2. Wire tab selection to `selectedTableId`

### Phase 5: Polish
1. Transition animations for pre-game to active mode switch
2. Overlay states for PAUSED and BALANCING
3. COMPLETED summary view

## Critical Files

- `src/app/game-lobby/game-lobby.component.ts` — main restructuring
- `src/app/game-lobby/game-lobby.component.html` — template rewrite
- `src/app/game/game-state.service.ts` — state selectors
- `src/app/game-page/cash-table/cash-table.component.ts` — embed in new layout
- `src/app/app.routes.ts` — route cleanup

## Verification

1. Run `npm start` and navigate through: login → home → register for game → join game
2. Verify pre-game layout shows leaderboard + messages (no empty tables column)
3. Verify active game shows table view inline with sidebar
4. Resize browser to check responsive breakpoints (mobile bottom nav vs desktop sidebar)
5. Run `npm test` to check for regressions
