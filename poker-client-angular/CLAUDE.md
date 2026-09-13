# Angular Poker Client - CLAUDE.md

## Project Overview
Angular 21 standalone-component poker game client. Connects to a REST + WebSocket backend at `http://localhost:8080`.

## Commands
```bash
npm start              # Dev server (ng serve)
npm test               # Run tests (jest --verbose)
npm run test:watch     # Watch mode
npm run test:coverage  # Coverage report
npm run build          # Production build
```

## Architecture

### Core Patterns
- **Standalone components** — No NgModules. Every component declares its own `imports` array.
- **Functional providers** — Guards use `CanActivateFn`, interceptors use `HttpInterceptorFn`.
- **RxJS / BehaviorSubject** — Primary async pattern; no NgRx or other state library.
- **`inject()`** — Preferred DI pattern over constructor injection.
- **`providedIn: 'root'`** — All services are root singletons.

### Don't
- Don't introduce NgModules, NgRx, or other state libraries.
- Don't use constructor injection — use `inject()`.
- Don't remove the `phaser` dependency — the production table renderer is `game-lobby/phaser-table/`. The legacy `game-lobby/css-poker-table/` is parked on disk as a short-term rollback and scheduled for deletion in a follow-up.

### Key Files
| File | Purpose |
|------|---------|
| `src/app/app.config.ts` | App providers (HTTP, router, error handler) |
| `src/app/app.routes.ts` | Routes — `''` (title page), `/home`, `/game/:gameId` |
| `src/app/rest/poker-rest-client.ts` | REST API client, base URL `http://localhost:8080` |
| `src/app/rest/rest.interceptor.ts` | Adds Bearer token; maps 4xx→ValidationError, 5xx→SystemError |
| `src/app/user/user-service.ts` | Auth state via BehaviorSubject; persists to `localStorage['currentUser']` |
| `src/app/game/cash-game.service.ts` | Cash game CRUD |
| `src/app/game/game-websocket.service.ts` | WebSocket lifecycle + command/event transport |
| `src/app/game/game-state.service.ts` | Observable game state derived from WS events |
| `src/app/error-handling/global-error-handler.ts` | Suppresses ValidationError, toasts SystemError |
| `src/app/modal/modal.service.ts` | Dynamic component modal system |
| `src/app/toaster/toaster.service.ts` | Toast notifications |
| `proxy.conf.json` | Dev proxy: `/api` → `http://localhost:8080` (strips `/api` prefix) |

### Feature Structure
```
src/app/
├── title-page/          # Login / register (entry point)
├── home-page/           # Game lobby list, create game
├── game-lobby/          # Active-game view (WebSocket-connected)
│   ├── phaser-table/    # Active table renderer (Phaser)
│   ├── css-poker-table/ # Legacy CSS renderer (parked, pending deletion)
│   ├── action-panel/
│   ├── leaderboard-panel/
│   ├── messages-panel/
│   └── table-view/
├── game-page/
│   ├── cash-table/      # Table visualization (SVG)
│   └── player/          # Player seat component
├── poker/card/          # Card rendering (SVG)
├── user/                # User models & service
├── game/                # Game models, REST service, WS service, events/commands
├── rest/                # HTTP client & interceptor
├── modal/               # Dialog system
├── toaster/             # Toast notifications
├── navigation-bar/
├── error-handling/
└── lib/                 # Shared utilities
```

## Styling
- **Tailwind CSS v3 + DaisyUI v4** — Utility-first with component presets.
- Enabled themes: coffee, forest, night, halloween, cupcake, emerald, fantasy, wireframe, winter (see `tailwind.config.js`).
- Global styles: `src/styles.css`.

## Auth
- JWT lives in `localStorage['currentUser']` as `{ ...user, token }`.
- `rest.interceptor.ts` attaches `Authorization: Bearer <token>` to outgoing requests.
- Guards: `authenticationGuard` (blocks unauthenticated), `loggedInGuard` (redirects logged-in users away from title page).

## Testing
- **Jest** (not Karma) — `*.spec.ts` co-located with source files.
- Config: `jest.config.ts`, setup: `setup-jest.ts`.

## TypeScript
- Strict mode: `strict`, `strictTemplates`, `strictInjectionParameters`.
- Target: ES2022.

## Error Handling
- `ValidationError` — 4xx HTTP responses; suppressed from user display.
- `SystemError` — 5xx HTTP responses; shown as toast.

## Backend API (localhost:8080)
- OpenAPI spec: http://localhost:8080/v3/api-docs
- Swagger UI: http://localhost:8080/swagger-ui/index.html

### WebSocket (game play)
- Command/event spec: http://localhost:8080/command-event-spec.md
- Connect: `ws://localhost:8080/ws/games/{gameId}?token={jwtToken}`
- Client: `src/app/game/game-websocket.service.ts`
- Types: `src/app/game/game-commands.ts`, `src/app/game/game-events.ts`

## Root-level scratch files
`PLAN.md`, `GAME-LOBBY-REDESIGN.md`, `poker-table-playground.html`, an are working notes / design experiments, not shipped artifacts.
