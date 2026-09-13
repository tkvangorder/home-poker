# Angular Client Monorepo Integration — Design

**Date:** 2026-09-13
**Status:** Approved

## Goal

Move the Angular poker client (currently a separate repository at
`~/work/angular/angular-poker-client`) into the `home-poker` Gradle build as the
sub-project `poker-client-angular`. During development the client and server
still run as two processes (`ng serve` on :4200 and Spring Boot on :8080). At
deployment time, `./gradlew build` produces a single bootJar that serves the
compiled Angular application from the same origin as the REST and WebSocket
endpoints.

The module is named `poker-client-angular` (not `poker-client`) to leave room
for a possible future React client.

## Non-goals

- No changes to any command, event, or REST endpoint path. `command-event-spec.md`
  is untouched.
- No reorganization of the client's internal source layout, playground HTML
  files, `PLAN.md`, or `GAME-LOBBY-REDESIGN.md`. They move as-is.
- No archival or deletion of the old client repository. That is a follow-up
  the user performs by hand.

## Section 1: Repository layout and import

- Import via `git subtree add --prefix=poker-client-angular <client-repo> main`
  so the client's full git history lands under `poker-client-angular/`. The
  client repo has one uncommitted change (`package-lock.json`); commit it in the
  old repo before importing.
- Add `poker-client-angular` to `settings.gradle` alongside `poker-common` and
  `poker-server`.
- Move `poker-client-angular/.claude/skills/phaser-dev` to the root
  `.claude/skills/phaser-dev`.
- Move `poker-client-angular/docs/superpowers/plans/*` and
  `.../specs/*` into the root `docs/superpowers/plans` and `specs`. There are
  no filename collisions.
- Delete `poker-client-angular/.claude/settings.json`,
  `.claude/settings.local.json`, `.superpowers/`, and `.worktrees/`. The root
  project already provides these.
- Keep `poker-client-angular/CLAUDE.md` as a nested project file, trimmed to
  client-only guidance (commands, architecture, conventions). Add a short
  client section to the root `CLAUDE.md` describing the module, how to run the
  two dev processes, and the single-jar deployment.
- Root `.gitignore` gains: `poker-client-angular/dist/`,
  `poker-client-angular/.angular/cache/`, `poker-client-angular/node/`,
  `poker-client-angular/coverage/`. The client's own `.gitignore` is removed
  (its `node_modules` and `.DS_Store` rules are already covered at the root).

## Section 2: Gradle build wiring

- `buildSrc/build.gradle` adds the `com.github.node-gradle:gradle-node-plugin`
  (7.x line) dependency so `poker-client-angular/build.gradle` can apply
  `com.github.node-gradle.node` by id without a version, matching how the
  Spring plugins are handled.
- `poker-client-angular/build.gradle`:
  - `node { download = true; version = '24.13.1'; npmInstallCommand = 'ci' }`.
    The version matches `.nvmrc`. Downloaded Node lives under the module's
    `.gradle/` and `node/` directories, both gitignored.
  - `npmBuild` (type `NpmTask`, `npm run build`). Inputs: `src/`,
    `angular.json`, `package.json`, `package-lock.json`, `tsconfig*.json`,
    `tailwind.config.js`. Output: `dist/poker-client-angular/browser`.
    Depends on `npmInstall`.
  - `npmTest` (type `NpmTask`, `npm test`). Attached to `check`, so
    `./gradlew build` runs Jest alongside the Java tests. Depends on
    `npmInstall`.
  - `angular.json` `outputPath` changes to `dist/poker-client-angular`.
- `poker-server/build.gradle`:
  - A `copyClientAssets` task (type `Copy`) that copies the output of
    `:poker-client-angular:npmBuild` into `build/resources/main/static/`.
  - `processResources` depends on `copyClientAssets`, so `bootJar` and
    `bootRun` include the client.
  - `test` and `compileJava` do not depend on the client. Running
    `./gradlew :poker-server:test` does not download Node or build the client.
    The `copyClientAssets` task only runs when `processResources` is requested.

    Note: `test` depends on `processResources` in the default Java plugin. To
    keep the client out of the test path, `copyClientAssets` is wired into
    `bootJar` and `bootRun` (via their `classpath` input / an explicit
    `dependsOn`) rather than into `processResources`. The implementation may
    instead add a separate `processClientResources` task feeding a dedicated
    source-set output directory that only `bootJar` and `bootRun` see. Either
    approach satisfies the requirement: `:poker-server:test` never triggers
    `npmBuild`.

## Section 3: Client URL changes

- `PokerRestClient.baseUrl` changes from `'/api'` to `''`, so calls go to
  `/auth/login`, `/cash-games/search`, `/users/...`, and `/files/...`.
- `GameWebSocketService` derives its base URL from `window.location`:
  `wss:` when `location.protocol` is `https:`, otherwise `ws:`, followed by
  `location.host` and `/ws/games`. When served from Spring this hits the same
  server; under `ng serve` it hits `localhost:4200` and the proxy forwards it.
- `proxy.conf.json` proxies these prefixes to `http://localhost:8080` with no
  path rewrite: `/auth`, `/users`, `/cash-games`, `/files`, `/admin`,
  `/swagger-ui`, `/v3`, and `/ws` (with `"ws": true`). Angular's own routes
  (`/`, `/home`, `/game/:gameId`) do not collide with any of these.
- Existing Jest specs referencing `/api/...` or the hardcoded WebSocket URL are
  updated to match.

## Section 4: Server changes for same-origin serving

- **SPA fallback.** A `SpaForwardingController` in `org.homepoker.rest` with
  explicit GET mappings for `/home` and `/game/{gameId}` that forward to
  `/index.html`. An explicit route list (not a catch-all) means unknown API
  paths still return 404 rather than the SPA shell. When a new client route is
  added, this controller must be updated.
- **Security.** `WebSecurityConfiguration` adds `permitAll` for `/`,
  `/index.html`, `/favicon.ico`, `/*.js`, `/*.css`, `/assets/**`, `/home`, and
  `/game/**`. The client authenticates its own API calls with a bearer token,
  so serving the shell unauthenticated is correct and exposes nothing.
- **CORS** in `WebConfig` stays as-is. It is harmless same-origin and still
  needed for `ng serve` on port 4200.
- **Docs.** Root `README.md` and `CLAUDE.md` describe the new module, the two
  dev processes (`./gradlew :poker-server:bootRun` and `npm start` inside
  `poker-client-angular`), and the single-jar deploy path.

## Section 5: Testing

- **Java integration test** (extends `BaseIntegrationTest`): with a stub
  `static/index.html` on the test classpath, assert that unauthenticated
  `GET /home` and `GET /game/abc` return the index content with 200; that
  `GET /cash-games/xyz` without a token still returns 401; and that an unknown
  path such as `GET /no-such-route` returns 404.
- **Client Jest**: update `PokerRestClient` and `GameWebSocketService` specs
  for the URL changes and run `npm test`.
- **Build**: `./gradlew clean build` produces a bootJar containing
  `BOOT-INF/classes/static/index.html`. `./gradlew :poker-server:test` succeeds
  and does not trigger `npmInstall` or `npmBuild`.
- **Manual**: run the jar, load `http://localhost:8080/`, log in, refresh on
  `/home`, and join a game so the WebSocket connects same-origin.
