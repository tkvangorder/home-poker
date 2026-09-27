# Design Decisions

The lasting design decisions behind features built so far: the *what* and the *why*. The game and table state machines are covered separately in [`cash-game-state-management.md`](cash-game-state-management.md). The wire format for every command and event is in [`command-event-spec.md`](poker-server/src/main/resources/static/command-event-spec.md).

This document was distilled from the original per-feature specs and plans (removed from `docs/superpowers/`; see git history) and checked against the code as of 2026-09. Where the code differs from the original spec, the code wins and this document describes the code.

---

## Server

### Event sequence numbers

**Problem:** clients had no way to detect a dropped event, so one lost message left them with corrupt state until the next snapshot.

- Every `GameEvent` carries `long sequenceNumber` and a `withSequenceNumber(long)` wither. Emitters construct events with `0`.
- **Events are stamped at fan-out** (`GameManager.stampEvent()`), not at construction. That way every listener sees the same number, and ordering is deterministic even when several commands emit in one tick.
- **Streams:** one game stream (`GameManager.gameStreamSeq`) and one stream per table (`TableManager.tableStreamSeq`). Both start at 1.
- **Type-check order in the stamping switch:** `UserEvent` → not stamped; `TableEvent` → table seq; `GameEvent` → game seq; plain `PokerEvent` (e.g. `SystemError`) → not stamped. `UserEvent` is checked first on purpose. `HoleCardsDealt` is both a `TableEvent` and a `UserEvent`, and stamping it would create false gaps for every other player.
- **Snapshots carry resume points:** `GameSnapshot.gameStreamSeq` + `tableStreamSeqs`, and `TableSnapshot.streamSeq`.
- **Recovery is snapshot-only.** Counters live in memory and reset on server restart. There is no replay by sequence range. The client-side gap rule is documented in `command-event-spec.md` ("Sequence Numbers & Gap Detection").

**Open follow-up (client):** the Angular client does not yet track sequence numbers or detect gaps. The intended design:
- Drop events until a snapshot baseline arrives. The snapshot is the source of truth at its resume point, so there is nothing to buffer.
- A table-stream gap wipes and re-fetches only that table.
- Presence starts as unknown, because snapshots don't carry it.

### Critical events: blinds and presence

- **`BlindPosted`** (TableEvent) is emitted from `TexasHoldemTableManager.postBlind()`.
  - All-in on a blind is expressed as `amountPosted` less than the blind; there is no separate event.
  - `BlindType` is SMALL/BIG, meant to be extended later (ANTE, STRADDLE, DEAD_BLIND).
  - Emission order at hand start: `BlindPosted(SMALL)` → `BlindPosted(BIG)` → `HandStarted` → `HoleCardsDealt` → `HandPhaseChanged` → `ActionOnPlayer`.
- **`PlayerDisconnected` / `PlayerReconnected`** (GameEvent).
  - A user may have several sockets, so `GameManager.activeListenerCounts` ref-counts listeners per user. Events fire only on 1→0 and 0→1 transitions.
  - Connect/disconnect is submitted as the internal commands `PlayerConnectedCommand` / `PlayerDisconnectedCommand` on the MPSC queue, so presence changes are serialized with player actions.

### Disconnect grace-period eviction

- `Player.disconnectedAt` is stamped on the 1→0 transition and cleared on 0→1. It is `@JsonIgnore`: never persisted or sent to clients.
- `GameManager.sweepDisconnectedPlayers()` runs each tick, only while the game is ACTIVE or BALANCING. It evicts players disconnected longer than `GameSettings.disconnectGraceSeconds` (120s, a code constant).
  - It runs **after** commands, so an explicit `LeaveGame` in the same tick wins.
  - It runs **before** `transitionGame`, so rebalancing sees the post-eviction seating.
- **Eviction reuses `removePlayerFromGame()`**, the same path as `LeaveGame`, so the two cannot drift.
  - A player in a hand is marked OUT and keeps the seat until the hand ends.
  - Otherwise the seat is vacated immediately.
  - Only a `GameMessage` is emitted; there is no dedicated "evicted" event.
- **The timer is in-memory only.** The `GameManager` constructor clears every `disconnectedAt` on load, since listener ref counts aren't persisted either, so everyone gets a fresh window after a restart.
- **`refreshDisconnectGraceTimestamps()`** resets pending stamps when the game enters ACTIVE (start or resume). Otherwise time spent while the sweep was off would evict someone on the very first tick.
- **No `Clock` abstraction.** Tests set `disconnectedAt` in the past (`DisconnectGraceEvictionTest`).

### Event store and admin hand replay

A forensic, god-view recording of every event, for admins to review hands.

- **`EventRecorder`** (`org.homepoker.recording`) is a `UserGameListener` that accepts *every* event, including other users' `UserEvent`s.
  - It tags table events with the current hand number (`currentHandByTable`).
  - Its body is wrapped in try/catch, so a recorder failure never breaks the game loop.
  - Its identity is `SystemUsers.EVENT_RECORDER_ID`. That identity is never persisted, and `removeGameListenersByUserId` refuses it.
- **`EventRecorderService` never blocks the game loop.**
  - Events go onto a bounded queue (`poker.recording.queue-capacity`, default 10000), drained by one virtual-thread worker.
  - On overflow the event is dropped and counted in `droppedEventCount`.
  - Serialization to the stored payload happens on the worker, not the loop thread.
  - `@PreDestroy` drains the queue.
- **Storage:** Mongo collection `recordedEvents`, indexed for replay (`MongoConfiguration`). `userId` on a record is the **recipient** of a `UserEvent`, never the actor.
- **Replay endpoint:** `GET /admin/replay/games/{gameId}/tables/{tableId}/hands/{handNumber}` (`ReplayController`).
  - Admin-only.
  - Results are ordered by `sequenceNumber`, then `recordedAt`.
  - An unknown hand returns an empty list.
- **Players are warned.** Replaying a hand from a game that isn't COMPLETED submits `AdminViewingReplayCommand`. The `GameManager` re-checks the admin role and broadcasts `AdminViewingReplay` to everyone. This extends the admin-visibility invariant in `CLAUDE.md`.
- **Known limitations:**
  - Recordings contain all hole cards, are kept indefinitely, and outlive deleted games. There is no TTL or retention policy.
  - After a restart, `seedHandTracker()` recovers hand numbers from the last 7 days of recordings.
  - Tests disable recording by passing a `null` `EventRecorderService` to `CashGameManager`.
- **Non-goals:** command replay (it would need a seedable production deck), state reconstruction, per-player-perspective replay, and an admin UI.

### Showdown winning-hand cards

- `ShowdownResult.Winner.winningCards` is the winner's best five cards, kickers included, as concrete cards (value and suit). The client highlights them without evaluating hands itself.
- **`HandResult.handCards`** is excluded from `equals`/`hashCode`/`compareTo`, so hand strength and tie logic are unaffected.
- **`BitwisePokerRanker.rankHand` resolves the cards after ranking** via `HandCardSelector.select()`, leaving the bitwise hot path untouched.
  - Each card is used once.
  - Flushes and straight flushes are restricted to the flush suit.
  - The A-5 wheel is handled.
  - `ClassicPokerRanker` does not populate `handCards`; only `BitwisePokerRanker` is used in production.
- A fold-win produces a "Last player standing" winner with empty `winningCards`.
- Only winners' revealed cards and community cards appear in the event.

### Deterministic decks and pot-distribution tests

- **`Deck(List<Card>)`** is a production constructor that skips shuffling.
  - Table managers get their deck from a `Supplier<Deck>`, via `GameManager.deckSupplier()`: `Deck::new` by default, overridden in tests.
  - This made it possible to test *who wins* each pot, which random decks never allowed.
- **Test harness** (`poker-server/src/test/java/org/homepoker/test/`): `DeckBuilder` (cards in deal order, `"As"`/`"Td"` notation), `SplitPotScenarioFixture`, and `ShowdownAssert` (checks winners, chops, odd chips and chip conservation). The scenarios live in `SplitPotScenariosTest`.
- **Pot-distribution rules as implemented:**
  - **Uncalled bets are not returned.** The excess forms its own pot with only the bettor eligible, and the bettor wins it back at showdown.
  - Folded contributors stay in a pot's eligible list; `evaluatePotWinners` skips folded seats at showdown.
  - The odd chip goes to the first winner left of the dealer.

---

## Client (`poker-client-angular`)

### Monorepo and combined build

- **The Angular client is a Gradle module**, `poker-client-angular`, built with `gradle-node-plugin` using a downloaded Node 24 and `npm ci`.
  - `npmBuild` hangs off `assemble`; `npmTest` hangs off `check`.
  - The module name leaves room for another client later.
- **Only `bootJar` and `bootRun` depend on `copyClientAssets`.** `:poker-server:test` never builds the client or downloads Node.
- **SPA routes are an explicit list** in `SpaForwardingController`, not a catch-all. A catch-all would turn unknown API paths into a 200 with the app shell.
  - A new client route must be added in both places; `app.routes.ts` has a comment pointing to the controller.
  - Unmapped static paths return 404 (`RestExceptionHandler`), without echoing the path.
- **The shell and static assets are public; the API is bearer-token only**, so serving the app unauthenticated is safe.
- **The client uses same-origin relative URLs.** The WebSocket URL is derived from `window.location`.
  - In development, `ng serve` proxies the API, the WebSocket and `/command-event-spec.md` to `:8080`.
  - CORS is kept for that dev loop.

### Phaser table renderer

- **Phaser replaced the original CSS table renderer**, which has since been deleted.
  - Visuals were tuned in a standalone, no-build page (`phaser-table-playground.html`) and copied into the game objects by hand. It is a design tool, not a shipped artifact.
- **Angular/Phaser boundary:**
  - `PhaserTableComponent` runs Phaser outside NgZone, to avoid needless change detection.
  - It pushes its inputs into a per-component `PhaserBridgeService`, a plain class holding BehaviorSubjects rather than an Angular injectable, which the scene reads.
- **Rendering:** the table redraws only when state changes. The one exception is the action timer, which updates every frame.
- **Timer denominator:** the server sends only `actionDeadline`. The client treats the time remaining when it first sees a deadline as 100%.
  - *Open follow-up:* expose `timeToActSeconds` from the server.
- **Layout:**
  - Nine seats evenly spaced, 40° apart, starting at bottom centre (`utils/seat-layout.ts`).
  - All sizes are ratios of the canvas width, with pixel minimums.
  - Colours are constants inside each game-object file.
- **Card privacy in the view:** the local user's cards are always face up; everyone else's follow `showCard`.
- **Testing:** only the pure helpers in `phaser-table/utils/` are unit-tested (Jest). Rendering is verified by hand.
- **Non-goals:** deal and chip-slide animations, avatar images, mobile tuning.

### Action feedback

- **`actionSeq`** (`game-state.service.ts`) increments on `player-acted` and `player-timed-out` and resets on `hand-started`. `lastAction` alone can't tell two identical consecutive actions apart, so the view could not re-trigger its feedback.
- **The action is shown by swapping the seat's name for the action text** in a per-action colour for 2 s (`ACTION_OVERLAY_MS`).
  - The logic lives in the pure, tested `computeBadgeState()`.
  - All-ins read "ALL IN".
  - There is no separate badge game object and no "TO ACT" badge; the gold glow and the timer bar mark whose turn it is.
- **Folded seats** fade the pod, show a muted "FOLDED" tag with the stack still visible, and grey-tint the hole cards.
- **Message log:**
  - Action lines ("Alice raises to $X (all in)").
  - A client-only `kind: 'action' | 'showdown'` tag on `ClientGameMessage`; the wire format is unchanged.
  - Showdown lines get a "WINNER:" prefix; action lines are muted.

### Showdown / pot-winner presentation

- **Winners are announced on a centre banner** (`winner-banner.ts`) above the community cards, not on the seat.
- **One pot at a time:**
  - `buildShowdownStages()` orders side pots smallest first and the main pot last. A split pot becomes one stage per winner.
  - Pot labels appear only when there are several pots.
  - Each stage shows for 4 s (`SHOWDOWN_STAGE_MS`).
- **The sequence starts when a new `potResults` object appears** (compared by identity, so unrelated updates don't restart it).
- **It aborts the instant `hand-started` clears it.** A showdown never delays the next hand.
- **The server's `winningCards` drive the highlight:** matched by value and suit (`card-match.ts`), with non-winning cards dimmed. The winning seat glows gold.
- **A fold-win shows only the banner and the seat glow.** This is intentional.
- **Non-goals:** chip-to-winner animation, sound, modal celebrations.
