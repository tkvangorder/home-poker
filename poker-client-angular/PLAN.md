# Poker Client UI - Implementation Plan

## Current State

Working foundation: login/register flows, title page, home page with mock game lists, static table preview with SVG rendering, card components, and infrastructure (REST client, interceptor, modal/toaster services, error handling). No WebSocket code exists yet, and the home page uses hardcoded mock data.

---

## Phase 1: Complete the Game Server Lobby (`/home`)

Wire the home page to real backend data and add game search/registration.

### 1.1 Add REST endpoints to `PokerRestClient`
- `GET /api/cash-games` - list available games
- `GET /api/cash-games/{id}` - game details
- `POST /api/cash-games/{id}/register` - register for a game
- `DELETE /api/cash-games/{id}/register` - unregister
- Admin endpoints for game management (create already exists, add update/delete)

### 1.2 Enhance `CashGameService`
- Add methods for listing, searching, and registering for games
- Expose an observable of games (polled or refreshed on actions)

### 1.3 Wire up `HomePageComponent`
- Replace mock data with real API calls
- Add search/filter controls (by name, status, date)
- Show "Registered" badge on games the user has joined
- Conditionally show admin controls (create/manage) based on `user.roles`

### 1.4 Game Details panel improvements
- Show registered players list
- Show game schedule/status
- "Register" / "Unregister" buttons
- "Join Game" button (enabled when game is in SEATING or ACTIVE state)

---

## Phase 2: WebSocket Service

Establish a reusable WebSocket connection layer for game communication.

### 2.1 Create `GameWebSocketService` (`src/app/game/game-websocket.service.ts`)
- `connect(gameId: string): Observable<GameEvent>` - opens `ws://localhost:8080/ws/games/{gameId}?token={jwt}`
- Parse incoming JSON messages, discriminate on `eventType`
- `sendCommand(command: GameCommand): void` - serialize and send
- Handle reconnection logic (exponential backoff)
- Handle connection lifecycle (connected/disconnected/error states)
- Clean up on disconnect (unsubscribe, close socket)

### 2.2 Define TypeScript interfaces for commands and events
- **Commands:** `RegisterForGame`, `StartGame`, `PauseGame`, `ResumeGame`, `EndGame`, `BuyIn`, `LeaveGame`, `PlayerActionCommand`, `PlayerIntent`, `ShowCards`, `PostBlind`
- **Events:** `GameStatusChanged`, `GameMessage`, `PlayerBuyIn`, `PlayerMoved`, `HandStarted`, `HoleCardsDealt`, `CommunityCardsDealt`, `PlayerActed`, `PlayerTimedOut`, `BettingRoundComplete`, `ShowdownResult`, `HandComplete`, `UserMessage`

### 2.3 Create `GameStateService`
- Reactive store that consumes WebSocket events and maintains current game state as observables
- Tracks: game status, players, tables, current hand state, pots, community cards
- Components subscribe to slices of this state

---

## Phase 3: Game Lobby (`/game/:gameId`)

Once a user joins a game, navigate them to a game-specific lobby.

### 3.1 Add route: `/game/:gameId` -> `GameLobbyComponent`
- Guarded by `authenticationGuard`
- On entry: connect WebSocket, subscribe to game events

### 3.2 `GameLobbyComponent` layout
- **Header:** Game name, status badge (SEATING / ACTIVE / PAUSED), admin controls (Start/Pause/Resume/End)
- **Leaderboard panel:** All players sorted by chip count with status indicators (ACTIVE, AWAY, OUT)
- **Tables panel:** List of active tables with player counts and current hand number
- **Chat/Messages panel:** Display `GameMessage` and `UserMessage` events
- **Buy-in controls:** Button to buy in (with amount input)

### 3.3 Navigation flow
- From home page "Join Game" -> `/game/:gameId` (lobby)
- From lobby, click a table -> renders table view (inline or navigated)

---

## Phase 4: Table View (`CashTableComponent` rework)

Make the table component data-driven from WebSocket events.

### 4.1 Rework `CashTableComponent`
- `@Input() tableId: string`
- Subscribe to `GameStateService` for table-specific state
- Render players at server-assigned `seatPosition`
- Show community cards as they arrive (`CommunityCardsDealt` events)
- Show pot totals from `BettingRoundComplete` events
- Highlight dealer, small blind, big blind positions
- Highlight current player to act

### 4.2 Rework `PlayerComponent`
- `@Input() player` with seat position, chip count, status, cards
- Show hole cards only for the current user (`HoleCardsDealt`) - others see card backs
- Show player action labels (Fold, Check, Call $X, Raise $X) as they happen
- Visual states: active turn (highlighted), folded (dimmed), away (indicator), timed out
- Action timer bar when it's the player's turn

### 4.3 Player action controls (current user only)
- Fold / Check / Call / Bet / Raise buttons
- Bet/Raise slider or input for amount
- Pre-action intents (Check/Fold, Call Any) using `PlayerIntent` command
- "Show Cards" button during `HAND_COMPLETE` phase

### 4.4 Hand result display
- `ShowdownResult` -> reveal winning hands, animate chips to winner
- `HandComplete` -> brief review period, then transition to next hand

---

## Phase 5: Multi-Table Observation

Allow users to observe other tables if screen space permits.

### 5.1 Responsive layout in Game Lobby
- Primary table (user's assigned table) always visible and largest
- On wider screens (>=1440px), show 1-2 observer tables in sidebar or below
- Observer tables are smaller, read-only versions of `CashTableComponent`
- Click an observer table to swap it with primary view

### 5.2 Implementation approach
- All table state comes through the same WebSocket connection (events include `tableId`)
- `GameStateService` tracks state for all tables, components filter by `tableId`
- Observer tables don't show hole cards (server won't send `HoleCardsDealt` for other tables)

---

## Implementation Order

| Order | Phase | Rationale |
|-------|-------|-----------|
| 1 | Phase 2 (WebSocket + Types) | Foundation everything else depends on |
| 2 | Phase 1 (Server Lobby) | REST wiring; can work in parallel with Phase 2 |
| 3 | Phase 3 (Game Lobby) | First consumer of WebSocket service |
| 4 | Phase 4 (Table View) | Heaviest UI work, needs stable WebSocket layer |
| 5 | Phase 5 (Multi-Table) | Enhancement once single-table works |

---

## Key Architecture Decisions

### RxJS WebSocket
Use `webSocket()` from `rxjs/webSocket` for an Observable-based WebSocket. Fits the existing RxJS/BehaviorSubject patterns without adding dependencies like socket.io.

### State Management
Continue with BehaviorSubject-based services. `GameStateService` holds full game state and exposes observable slices. Use `distinctUntilChanged` and selective updates to avoid unnecessary re-renders during active play.

### Event Discrimination
Use a discriminated union pattern with `eventType` as the discriminator:
```typescript
type GameEvent = GameStatusChanged | HandStarted | PlayerActed | ...;
```
Then `switch(event.eventType)` handles each case with full type narrowing.

### Table Component Strategy
Keep the SVG-based approach but make it purely reactive (inputs + state service). The 9-seat layout works well. Consider making seat count configurable since the server manages table sizes.

**Investigation: Phaser.js** - Before building out Phase 4, evaluate whether Phaser.js (a 2D game framework with Canvas/WebGL rendering) would be a better fit than pure CSS/HTML/SVG for the table view. Phaser could provide smoother animations (chip movements, card dealing, pot collection), better performance with many visual elements, and a more polished game feel. The tradeoff is added complexity and a departure from standard Angular rendering. Spike this early in Phase 4 to make an informed decision before committing to either approach.

### WebSocket Connection Details
- Endpoint: `ws://localhost:8080/ws/games/{gameId}?token={jwt}`
- JWT obtained from REST login, stored in localStorage
- Commands sent as JSON with `commandId` discriminator (kebab-case)
- Events received as JSON with `eventType` discriminator (kebab-case)
- Server injects `user` field on commands - client never sends it
