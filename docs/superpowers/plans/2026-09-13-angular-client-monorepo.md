# Angular Client Monorepo Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Angular poker client into this repo as the Gradle sub-project `poker-client-angular`, and have `./gradlew build` produce one bootJar that serves the compiled client from the same origin as the API.

**Architecture:** The client is imported with `git subtree` (history preserved) and wrapped in a Gradle module that uses the `com.github.node-gradle.node` plugin to run `npm ci`, `npm run build`, and `npm test`. The server copies the built `browser/` output into a dedicated `build/client-resources/static` directory that only `bootJar` and `bootRun` see, so Java tests never touch Node. The client switches to same-origin relative URLs, and the server gains an explicit SPA-forwarding controller plus `permitAll` rules for the static shell.

**Tech Stack:** Gradle 9.3.1 (Groovy DSL, convention plugins in `buildSrc`), Spring Boot 4.0.5, Java 25, Angular 21, Node 24.13.1, Jest 30, gradle-node-plugin 7.1.0.

**Spec:** `docs/superpowers/specs/2026-09-13-angular-client-monorepo-design.md`

## Global Constraints

- Module name is exactly `poker-client-angular`. Never `poker-client`.
- Node version pinned to `24.13.1` (matches the client's `.nvmrc`).
- gradle-node-plugin version `7.1.0`.
- `./gradlew :poker-server:test` must never trigger `npmInstall` or `npmBuild`.
- No command, event, or REST endpoint path changes. `command-event-spec.md` is untouched.
- Every commit message ends with:
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MhZV14toYwnATRwLLKG4mN
  ```
- The project's `PostToolUse` hook runs `./gradlew :poker-server:test` whenever a file under `poker-server/src/**/java` is edited. That is expected; let it run.
- Do all work on a feature branch (`feature/angular-client-monorepo`) branched from `main`.

---

### Task 1: Import the client with git subtree and register the module

**Files:**
- Modify: `settings.gradle`
- Modify: `.gitignore`
- Create (via subtree): `poker-client-angular/**`
- Move: `poker-client-angular/.claude/skills/phaser-dev` → `.claude/skills/phaser-dev`
- Move: `poker-client-angular/docs/superpowers/plans/*` → `docs/superpowers/plans/`
- Move: `poker-client-angular/docs/superpowers/specs/*` → `docs/superpowers/specs/`
- Delete: `poker-client-angular/.claude/settings.json`, `poker-client-angular/.claude/settings.local.json`, `poker-client-angular/.gitignore`
- Modify: `.claude/settings.json` (enable the `frontend-design` plugin the client used)

**Interfaces:**
- Produces: the directory `poker-client-angular/` containing `package.json`, `angular.json`, `src/`, `proxy.conf.json`, `.nvmrc`, `CLAUDE.md`, and a Gradle project path `:poker-client-angular` (no build file yet; Task 2 adds it).

- [ ] **Step 1: Commit the pending lock-file change in the old client repo**

The old repo has one uncommitted change to `package-lock.json`. `git subtree add` imports a ref, so commit it first.

```bash
cd ~/work/angular/angular-poker-client
git status --short          # expect: " M package-lock.json"
git add package-lock.json
git commit -m "chore: refresh package-lock.json"
git status --short          # expect: empty
git rev-parse --abbrev-ref HEAD   # expect: main
```

- [ ] **Step 2: Create the feature branch in home-poker**

```bash
cd ~/work/home-poker
git status --short          # expect: empty (clean tree required for subtree add)
git checkout -b feature/angular-client-monorepo
```

- [ ] **Step 3: Import the client under poker-client-angular/**

```bash
cd ~/work/home-poker
git subtree add --prefix=poker-client-angular ~/work/angular/angular-poker-client main
```

Expected: a merge commit titled `Add 'poker-client-angular/' from commit '<sha>'`. Verify:

```bash
ls poker-client-angular            # expect: angular.json package.json src proxy.conf.json CLAUDE.md ...
git log --oneline -3 -- poker-client-angular/package.json   # expect: original client commits visible
```

- [ ] **Step 4: Relocate the phaser-dev skill and the client's superpowers docs**

```bash
cd ~/work/home-poker
git mv poker-client-angular/.claude/skills/phaser-dev .claude/skills/phaser-dev
git mv poker-client-angular/docs/superpowers/plans/* docs/superpowers/plans/
git mv poker-client-angular/docs/superpowers/specs/* docs/superpowers/specs/
git rm -q poker-client-angular/.claude/settings.json poker-client-angular/.claude/settings.local.json poker-client-angular/.gitignore
rm -rf poker-client-angular/.superpowers poker-client-angular/.worktrees poker-client-angular/.claude poker-client-angular/docs
ls .claude/skills                  # expect: add-event-type create-command game-state phaser-dev table-state test-game-scenario
ls docs/superpowers/plans | wc -l  # expect: 11 (5 server + 5 client + this plan)
```

The `phaser-dev` skill's `SKILL.md` may reference client paths like `src/app/...`. Open it and prefix any such path with `poker-client-angular/`:

```bash
grep -n "src/app\|angular.json\|npm " .claude/skills/phaser-dev/SKILL.md .claude/skills/phaser-dev/references/* | head -30
```

For each hit that is a repo-relative path, edit it to start with `poker-client-angular/`. Commands such as `npm test` should be prefixed with `cd poker-client-angular && `.

- [ ] **Step 5: Register the module and extend .gitignore**

Edit `settings.gradle` so the include line reads:

```groovy
include("poker-common", "poker-server", "poker-client-angular")
```

Append to the root `.gitignore`:

```gitignore

# Angular client (poker-client-angular)
poker-client-angular/dist/
poker-client-angular/.angular/
poker-client-angular/coverage/
poker-client-angular/node/
```

(`node_modules/`, `.gradle/`, `.DS_Store`, and `.idea/` are already covered by existing root patterns.)

- [ ] **Step 6: Enable the frontend-design plugin at the root**

Edit `.claude/settings.json` so `enabledPlugins` reads:

```json
  "enabledPlugins": {
    "skill-creator@claude-plugins-official": true,
    "frontend-design@claude-plugins-official": true
  },
```

- [ ] **Step 7: Verify Gradle sees the new project and nothing else broke**

```bash
cd ~/work/home-poker
./gradlew projects --quiet
```

Expected output includes:

```
Root project 'home-poker'
+--- Project ':poker-client-angular'
+--- Project ':poker-common'
\--- Project ':poker-server'
```

Then confirm the ignored directories are actually ignored:

```bash
git status --short | grep -c "node_modules\|poker-client-angular/dist\|poker-client-angular/.angular"   # expect: 0
```

- [ ] **Step 8: Commit**

```bash
cd ~/work/home-poker
git add -A
git commit -m "chore: import Angular client as poker-client-angular module

Imported via git subtree to preserve history. Relocated the phaser-dev
skill and the client's superpowers docs to the root, dropped the
client's own .claude settings and .gitignore, and registered the
module in settings.gradle.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MhZV14toYwnATRwLLKG4mN"
```

---

### Task 2: Gradle build for the Angular module

**Files:**
- Modify: `buildSrc/build.gradle`
- Create: `poker-client-angular/build.gradle`
- Modify: `poker-client-angular/angular.json` (`outputPath`)

**Interfaces:**
- Consumes: the `:poker-client-angular` project path from Task 1.
- Produces: Gradle tasks `:poker-client-angular:npmInstall`, `:poker-client-angular:npmBuild`, `:poker-client-angular:npmTest`. `npmBuild` writes the compiled site to `poker-client-angular/dist/poker-client-angular/browser/` (contains `index.html`, `main-*.js`, `styles-*.css`, `favicon.ico`, `assets/`).

- [ ] **Step 1: Add the node plugin to buildSrc**

Edit `buildSrc/build.gradle` `dependencies` block to:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-gradle-plugin:4.0.5'
    implementation 'io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7'
    implementation 'com.github.node-gradle:gradle-node-plugin:7.1.0'
}
```

- [ ] **Step 2: Point the Angular build output at the module name**

In `poker-client-angular/angular.json`, change:

```json
            "outputPath": "dist/angular-poker-client",
```

to:

```json
            "outputPath": "dist/poker-client-angular",
```

- [ ] **Step 3: Create the module build file**

Create `poker-client-angular/build.gradle`:

```groovy
plugins {
    id 'base'
    id 'com.github.node-gradle.node'
}

node {
    download = true
    version = '24.13.1'
    npmInstallCommand = 'ci'
}

def clientInputs = [
    'angular.json', 'package.json', 'package-lock.json',
    'tsconfig.json', 'tsconfig.app.json', 'tsconfig.spec.json',
    'tailwind.config.js', 'jest.config.ts', 'setup-jest.ts'
]

// Compiles the Angular application. Output is consumed by :poker-server:copyClientAssets.
tasks.register('npmBuild', com.github.gradle.node.npm.task.NpmTask) {
    description = 'Builds the Angular client (ng build).'
    group = 'build'
    dependsOn tasks.named('npmInstall')
    args = ['run', 'build']
    inputs.dir('src')
    inputs.files(clientInputs)
    outputs.dir(layout.projectDirectory.dir('dist/poker-client-angular'))
}

// Runs the Jest suite. Attached to `check` so `./gradlew build` runs it alongside the Java tests.
tasks.register('npmTest', com.github.gradle.node.npm.task.NpmTask) {
    description = 'Runs the Angular client unit tests (jest).'
    group = 'verification'
    dependsOn tasks.named('npmInstall')
    args = ['test']
    inputs.dir('src')
    inputs.files(clientInputs)
    outputs.dir(layout.projectDirectory.dir('coverage'))
}

tasks.named('check') {
    dependsOn tasks.named('npmTest')
}

tasks.named('assemble') {
    dependsOn tasks.named('npmBuild')
}

tasks.named('clean') {
    delete layout.projectDirectory.dir('dist')
}
```

- [ ] **Step 4: Run the client build through Gradle**

```bash
cd ~/work/home-poker
./gradlew :poker-client-angular:npmBuild
```

Expected: Gradle downloads Node 24.13.1 into `poker-client-angular/.gradle/nodejs/` on first run, runs `npm ci`, then `ng build`. Task ends with `BUILD SUCCESSFUL`. Verify output:

```bash
ls poker-client-angular/dist/poker-client-angular/browser/
# expect: index.html, main-*.js, polyfills-*.js, styles-*.css, favicon.ico, assets/ (and possibly media/, chunk-*.js)
git status --short poker-client-angular | head    # expect: only angular.json and build.gradle changed; no dist/.gradle/.angular noise
```

- [ ] **Step 5: Confirm up-to-date checking works and Jest runs through Gradle**

```bash
./gradlew :poker-client-angular:npmBuild        # expect: "> Task :poker-client-angular:npmBuild UP-TO-DATE"
./gradlew :poker-client-angular:npmTest         # expect: Jest output, all suites pass, BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add buildSrc/build.gradle poker-client-angular/build.gradle poker-client-angular/angular.json
git commit -m "build: wrap Angular client in a Gradle module with node plugin

Adds npmInstall/npmBuild/npmTest tasks backed by a Gradle-downloaded
Node 24.13.1. npmTest is attached to check; npmBuild to assemble.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MhZV14toYwnATRwLLKG4mN"
```

---

### Task 3: Package the client into the server bootJar and bootRun

**Files:**
- Modify: `poker-server/build.gradle`

**Interfaces:**
- Consumes: `:poker-client-angular:npmBuild` and its output dir `poker-client-angular/dist/poker-client-angular/browser`.
- Produces: `:poker-server:copyClientAssets` writing to `poker-server/build/client-resources/static/`, which `bootJar` packages as `BOOT-INF/classes/static/` and `bootRun` places on the classpath.

- [ ] **Step 1: Add the copy task and wire it into bootJar and bootRun**

Append to `poker-server/build.gradle` (after the existing `dependencies` block):

```groovy
// ---------------------------------------------------------------------------
// Angular client packaging.
//
// The compiled client is copied into build/client-resources/static, a directory
// that is deliberately NOT an output of processResources. Only bootJar and
// bootRun depend on it, so `./gradlew :poker-server:test` never builds the
// client or downloads Node.
// ---------------------------------------------------------------------------
def clientResources = layout.buildDirectory.dir('client-resources')

tasks.register('copyClientAssets', Copy) {
    description = 'Copies the compiled Angular client into the server static resources.'
    group = 'build'
    dependsOn ':poker-client-angular:npmBuild'
    from(rootProject.file('poker-client-angular/dist/poker-client-angular/browser'))
    into(clientResources.map { it.dir('static') })
}

tasks.named('bootJar') {
    dependsOn tasks.named('copyClientAssets')
    classpath(clientResources)
}

tasks.named('bootRun') {
    dependsOn tasks.named('copyClientAssets')
    classpath(clientResources)
}
```

- [ ] **Step 2: Prove tests do not pull in the client**

```bash
cd ~/work/home-poker
./gradlew :poker-server:test --dry-run | grep -c "npm\|copyClientAssets"     # expect: 0
./gradlew :poker-server:bootJar --dry-run | grep "npmBuild\|copyClientAssets"
# expect both lines:
#   :poker-client-angular:npmBuild SKIPPED
#   :poker-server:copyClientAssets SKIPPED
```

- [ ] **Step 3: Build the jar and inspect it**

```bash
./gradlew :poker-server:bootJar
unzip -l poker-server/build/libs/poker-server-0.0.1-SNAPSHOT.jar | grep "BOOT-INF/classes/static/" | head
```

Expected: lines for `BOOT-INF/classes/static/index.html`, `BOOT-INF/classes/static/main-*.js`, `BOOT-INF/classes/static/assets/...`, and the pre-existing `BOOT-INF/classes/static/command-event-spec.md`.

- [ ] **Step 4: Commit**

```bash
git add poker-server/build.gradle
git commit -m "build: package compiled Angular client into the server bootJar

copyClientAssets feeds build/client-resources/static, which only bootJar
and bootRun consume. Server tests stay Node-free.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MhZV14toYwnATRwLLKG4mN"
```

---

### Task 4: Client uses same-origin relative URLs

**Files:**
- Modify: `poker-client-angular/src/app/rest/poker-rest-client.ts:15`
- Modify: `poker-client-angular/src/app/game/game-websocket.service.ts:19,37`
- Modify: `poker-client-angular/proxy.conf.json`
- Create: `poker-client-angular/src/app/rest/poker-rest-client.spec.ts`
- Create: `poker-client-angular/src/app/game/game-websocket.service.spec.ts`

**Interfaces:**
- Produces: `PokerRestClient.baseUrl === ''`; `GameWebSocketService.buildSocketUrl(gameId: string, token: string): string` (public, pure, derives protocol and host from `window.location`).

- [ ] **Step 1: Write the failing REST client spec**

Create `poker-client-angular/src/app/rest/poker-rest-client.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { PokerRestClient } from './poker-rest-client';

describe('PokerRestClient', () => {
  let client: PokerRestClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    client = TestBed.inject(PokerRestClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts login to the same-origin /auth/login path with no /api prefix', () => {
    client.login('alice', 'secret').subscribe();

    const req = http.expectOne('/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ loginId: 'alice', password: 'secret' });
    req.flush({});
  });

  it('posts game searches to /cash-games/search', () => {
    client.searchGames({} as any).subscribe();

    const req = http.expectOne('/cash-games/search');
    expect(req.request.method).toBe('POST');
    req.flush([]);
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd ~/work/home-poker/poker-client-angular
npx jest src/app/rest/poker-rest-client.spec.ts
```

Expected: FAIL. Message includes `Expected one matching request for criteria "Match URL: /auth/login", found none` and mentions the actual request went to `/api/auth/login`.

- [ ] **Step 3: Drop the /api prefix**

In `poker-client-angular/src/app/rest/poker-rest-client.ts`, change line 15 from:

```ts
  baseUrl: string = '/api';
```

to:

```ts
  // Same-origin relative paths. In production the Spring server serves both the
  // client and the API; under `ng serve` proxy.conf.json forwards these prefixes.
  baseUrl: string = '';
```

- [ ] **Step 4: Run the REST spec to verify it passes**

```bash
npx jest src/app/rest/poker-rest-client.spec.ts
```

Expected: PASS, 2 tests.

- [ ] **Step 5: Write the failing WebSocket URL spec**

Create `poker-client-angular/src/app/game/game-websocket.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { GameWebSocketService } from './game-websocket.service';
import { UserService } from '../user/user-service';

describe('GameWebSocketService.buildSocketUrl', () => {
  let service: GameWebSocketService;

  function stubLocation(protocol: string, host: string) {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { protocol, host },
    });
  }

  const originalLocation = window.location;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        GameWebSocketService,
        { provide: UserService, useValue: { getCurrentUser: () => null } },
      ],
    });
    service = TestBed.inject(GameWebSocketService);
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    });
  });

  it('uses ws:// and the page host for an http page', () => {
    stubLocation('http:', 'localhost:4200');
    expect(service.buildSocketUrl('game-1', 'tok')).toBe(
      'ws://localhost:4200/ws/games/game-1?token=tok'
    );
  });

  it('uses wss:// for an https page', () => {
    stubLocation('https:', 'poker.example.com');
    expect(service.buildSocketUrl('game-1', 'tok')).toBe(
      'wss://poker.example.com/ws/games/game-1?token=tok'
    );
  });
});
```

- [ ] **Step 6: Run it to verify it fails**

```bash
npx jest src/app/game/game-websocket.service.spec.ts
```

Expected: FAIL with `TypeError: service.buildSocketUrl is not a function`.

- [ ] **Step 7: Derive the WebSocket URL from window.location**

In `poker-client-angular/src/app/game/game-websocket.service.ts`, replace line 19:

```ts
  private wsBaseUrl = 'ws://localhost:8080/ws/games';
```

with nothing (delete it), and replace line 37:

```ts
    const url = `${this.wsBaseUrl}/${gameId}?token=${user.token}`;
```

with:

```ts
    const url = this.buildSocketUrl(gameId, user.token);
```

Then add this method to the class, directly above `connect(...)`:

```ts
  /**
   * Builds the game WebSocket URL relative to the page origin. In production
   * the Spring server serves both the client and the WebSocket endpoint; under
   * `ng serve` the dev proxy forwards `/ws` to the server.
   */
  buildSocketUrl(gameId: string, token: string): string {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/ws/games/${gameId}?token=${token}`;
  }
```

- [ ] **Step 8: Run the WebSocket spec to verify it passes**

```bash
npx jest src/app/game/game-websocket.service.spec.ts
```

Expected: PASS, 2 tests.

- [ ] **Step 9: Update the dev proxy**

Replace the contents of `poker-client-angular/proxy.conf.json` with:

```json
{
  "/auth": { "target": "http://localhost:8080", "secure": false },
  "/users": { "target": "http://localhost:8080", "secure": false },
  "/cash-games": { "target": "http://localhost:8080", "secure": false },
  "/files": { "target": "http://localhost:8080", "secure": false },
  "/admin": { "target": "http://localhost:8080", "secure": false },
  "/swagger-ui": { "target": "http://localhost:8080", "secure": false },
  "/v3": { "target": "http://localhost:8080", "secure": false },
  "/command-event-spec.md": { "target": "http://localhost:8080", "secure": false },
  "/ws": { "target": "http://localhost:8080", "secure": false, "ws": true }
}
```

- [ ] **Step 10: Run the whole Jest suite and confirm nothing else referenced the old URLs**

```bash
cd ~/work/home-poker/poker-client-angular
grep -rn "/api\|localhost:8080" src   # expect: no hits
npm test 2>&1 | tail -15               # expect: all suites pass
```

- [ ] **Step 11: Commit**

```bash
cd ~/work/home-poker
git add poker-client-angular/src/app/rest/poker-rest-client.ts \
        poker-client-angular/src/app/rest/poker-rest-client.spec.ts \
        poker-client-angular/src/app/game/game-websocket.service.ts \
        poker-client-angular/src/app/game/game-websocket.service.spec.ts \
        poker-client-angular/proxy.conf.json
git commit -m "feat(client): use same-origin relative URLs for REST and WebSocket

REST calls drop the /api prefix and the WebSocket URL is derived from
window.location, so the client works unchanged when served by Spring.
The ng serve proxy now forwards the server's real path prefixes.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MhZV14toYwnATRwLLKG4mN"
```

---

### Task 5: Server serves the SPA shell and permits static assets

**Files:**
- Create: `poker-server/src/main/java/org/homepoker/rest/SpaForwardingController.java`
- Modify: `poker-server/src/main/java/org/homepoker/security/WebSecurityConfiguration.java:30`
- Create: `poker-server/src/test/resources/static/index.html`
- Create: `poker-server/src/test/java/org/homepoker/rest/SpaForwardingControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `BaseIntegrationTest` (`org.homepoker.test`) with its `createUser(User)` and `userManager` members; `TestDataHelper.user(loginId, password, name)`; `JwtTokenService.generateToken(User)`.
- Produces: GET `/home` and GET `/game/{gameId}` forward to `/index.html`.

- [ ] **Step 1: Add a stub index.html to the test classpath**

Create `poker-server/src/test/resources/static/index.html`:

```html
<!doctype html>
<html><body>SPA-TEST-SHELL</body></html>
```

- [ ] **Step 2: Write the failing integration test**

Create `poker-server/src/test/java/org/homepoker/rest/SpaForwardingControllerIntegrationTest.java`. The login id `admin` is listed under `adminUsers` in `application-test.yml`, so registering it through `createUser` assigns the ADMIN role automatically (same approach as `ReplayControllerIntegrationTest`):

```java
package org.homepoker.rest;

import org.homepoker.model.user.User;
import org.homepoker.security.JwtTokenService;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Angular shell is served unauthenticated for client routes,
 * that API paths are still protected, and that unknown paths are not swallowed
 * by the SPA fallback. A stub {@code static/index.html} lives on the test
 * classpath so this test does not depend on the real client build.
 */
class SpaForwardingControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired JwtTokenService jwtTokenService;

  private RestTestClient client;

  @BeforeEach
  void setUpClient() {
    client = RestTestClient.bindToServer()
        .baseUrl("http://localhost:" + serverPort)
        .build();
  }

  @Test
  void rootServesIndexWithoutAuthentication() {
    client.get().uri("/")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("SPA-TEST-SHELL"));
  }

  @Test
  void homeRouteForwardsToIndexWithoutAuthentication() {
    client.get().uri("/home")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("SPA-TEST-SHELL"));
  }

  @Test
  void gameRouteForwardsToIndexWithoutAuthentication() {
    client.get().uri("/game/abc-123")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("SPA-TEST-SHELL"));
  }

  @Test
  void apiPathsStillRequireAuthentication() {
    client.get().uri("/cash-games/some-game")
        .exchange()
        .expectStatus().isForbidden();
  }

  @Test
  void unknownPathIsNotSwallowedBySpaFallback() {
    client.get().uri("/no-such-route")
        .headers(h -> h.setBearerAuth(adminToken()))
        .exchange()
        .expectStatus().isNotFound();
  }

  private String adminToken() {
    User admin = createUser(TestDataHelper.user("admin", "password", "Admin"));
    return jwtTokenService.generateToken(admin);
  }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
cd ~/work/home-poker
./gradlew :poker-server:test --tests "org.homepoker.rest.SpaForwardingControllerIntegrationTest"
```

Expected: `rootServesIndexWithoutAuthentication`, `homeRouteForwardsToIndexWithoutAuthentication`, and `gameRouteForwardsToIndexWithoutAuthentication` FAIL with status 403 (security rejects them). `apiPathsStillRequireAuthentication` and `unknownPathIsNotSwallowedBySpaFallback` should already pass.

- [ ] **Step 4: Create the forwarding controller**

Create `poker-server/src/main/java/org/homepoker/rest/SpaForwardingController.java`:

```java
package org.homepoker.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards the Angular client's routes to {@code index.html} so a browser
 * refresh or a deep link on a client route loads the SPA shell.
 * <p>
 * The route list is explicit on purpose: a catch-all would turn every unknown
 * API path into a 200 with the SPA shell. When a route is added to
 * {@code poker-client-angular/src/app/app.routes.ts}, add it here as well.
 */
@Controller
public class SpaForwardingController {

  @GetMapping({"/home", "/game/{gameId}"})
  public String forwardToIndex() {
    return "forward:/index.html";
  }
}
```

- [ ] **Step 5: Permit the static shell in the security configuration**

In `poker-server/src/main/java/org/homepoker/security/WebSecurityConfiguration.java`, replace the single `requestMatchers(...).permitAll()` line (line 30) with two lines:

```java
                .requestMatchers("/auth/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/ws/**", "/command-event-spec.md").permitAll()
                // Angular client shell and static assets. The client authenticates its own API calls with a bearer token.
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/*.js", "/*.css", "/assets/**", "/media/**", "/home", "/game/**").permitAll()
```

Add the import:

```java
import org.springframework.http.HttpMethod;
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :poker-server:test --tests "org.homepoker.rest.SpaForwardingControllerIntegrationTest"
```

Expected: 5 tests PASS. If `unknownPathIsNotSwallowedBySpaFallback` returns something other than 404, inspect the response body; a global exception handler may wrap 404s. Adjust the assertion to whatever the app returns for a genuinely unmapped authenticated GET, and record the reason in a comment. Do not loosen it to "not 200".

- [ ] **Step 7: Run the full server suite**

```bash
./gradlew :poker-server:test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL. (The `PostToolUse` hook will also have run this in the background after each Java edit.)

- [ ] **Step 8: Commit**

```bash
git add poker-server/src/main/java/org/homepoker/rest/SpaForwardingController.java \
        poker-server/src/main/java/org/homepoker/security/WebSecurityConfiguration.java \
        poker-server/src/test/resources/static/index.html \
        poker-server/src/test/java/org/homepoker/rest/SpaForwardingControllerIntegrationTest.java
git commit -m "feat(server): serve the Angular shell same-origin with SPA route forwarding

Explicit GET forwards for /home and /game/{id} to index.html, and
permitAll for the static shell and asset paths. API paths remain
protected and unknown paths still 404.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MhZV14toYwnATRwLLKG4mN"
```

---

### Task 6: Documentation for the combined repo

**Files:**
- Modify: `CLAUDE.md` (root)
- Modify: `README.md` (root)
- Modify: `poker-client-angular/CLAUDE.md`
- Modify: `poker-client-angular/README.md`

- [ ] **Step 1: Add the client module to the root CLAUDE.md**

In the root `CLAUDE.md`, under `## Build & Run`, replace the code block with:

```bash
# Build all modules (Java + Angular client; downloads Node 24 on first run)
./gradlew clean build

# Run the server with the compiled client served from http://localhost:8080 (requires MongoDB)
./gradlew :poker-server:bootRun

# Client dev loop: run the server as above, then in a second terminal
cd poker-client-angular && npm start        # http://localhost:4200, proxies API + WebSocket to :8080

# Start MongoDB via Docker Compose
docker-compose up
```

Under `## Testing`, add after the `./gradlew :poker-common:test` line:

```bash
./gradlew :poker-client-angular:npmTest     # Angular unit tests (Jest) through Gradle
cd poker-client-angular && npm test         # same tests, direct
```

Under `## Module Structure`, add a third module entry after `poker-server`:

```markdown
**poker-client-angular** — Angular 21 browser client. See `poker-client-angular/CLAUDE.md` for client conventions.
- Built by Gradle via the node plugin; `:poker-server:bootJar` and `bootRun` copy its `dist/.../browser` output into `static/`.
- `:poker-server:test` never builds the client.
- Client routes (`/home`, `/game/:id`) are forwarded to `index.html` by `SpaForwardingController`. Add new client routes there too.
```

- [ ] **Step 2: Update the client CLAUDE.md for its new home**

In `poker-client-angular/CLAUDE.md`:

Replace the `## Commands` code block with:

```bash
# All commands run from poker-client-angular/
npm start              # Dev server on :4200 (ng serve, proxies API + WS to :8080)
npm test               # Run tests (jest --verbose)
npm run test:watch     # Watch mode
npm run test:coverage  # Coverage report
npm run build          # Production build to dist/poker-client-angular/browser

# From the repo root, through Gradle (downloads its own Node 24):
./gradlew :poker-client-angular:npmBuild
./gradlew :poker-client-angular:npmTest
```

In the `### Key Files` table, change the `poker-rest-client.ts` row's purpose to `REST API client, same-origin relative paths (no prefix)` and the `proxy.conf.json` row to ``Dev proxy: forwards `/auth`, `/users`, `/cash-games`, `/files`, `/admin`, `/swagger-ui`, `/v3`, `/ws` to `http://localhost:8080` ``.

Replace the `## Backend API (localhost:8080)` section heading and `### WebSocket` connect line with:

```markdown
## Backend API
The server lives in `../poker-server`. In production it serves this client from the same origin, so all client URLs are relative.
- OpenAPI spec: http://localhost:8080/v3/api-docs
- Swagger UI: http://localhost:8080/swagger-ui/index.html

### WebSocket (game play)
- Command/event spec: `../poker-server/src/main/resources/static/command-event-spec.md`
- Connect: `${ws|wss}://${location.host}/ws/games/{gameId}?token={jwtToken}` (built by `GameWebSocketService.buildSocketUrl`)
- Client: `src/app/game/game-websocket.service.ts`
- Types: `src/app/game/game-commands.ts`, `src/app/game/game-events.ts`
```

Add a top-level note directly under the `# Angular Poker Client - CLAUDE.md` heading:

```markdown
This module is `poker-client-angular` inside the `home-poker` Gradle build. Root-level guidance in `../CLAUDE.md` applies too.
```

- [ ] **Step 3: Replace the generated client README**

Overwrite `poker-client-angular/README.md` with:

```markdown
# poker-client-angular

Angular 21 browser client for the home-poker server. This module is part of the `home-poker` Gradle build; at deployment time the compiled client is packaged into the server jar and served from the same origin as the REST and WebSocket endpoints.

## Development

Run the server from the repo root (`./gradlew :poker-server:bootRun`, MongoDB required), then:

```bash
cd poker-client-angular
npm install        # first time only; Gradle uses `npm ci` with its own Node, but local dev can use your own Node 24
npm start          # http://localhost:4200 — proxy.conf.json forwards API and WebSocket traffic to :8080
npm test           # Jest
```

## Production build

```bash
./gradlew build    # from the repo root; runs ng build + jest and packages the client into poker-server's bootJar
```

The client output lands in `dist/poker-client-angular/browser/` and is copied to `poker-server/build/client-resources/static/`.
```

- [ ] **Step 4: Update the root README**

In the root `README.md`, replace the first paragraph's last sentence (`The plan is to implement a server first and then build (or recruit someone to build) a client using something like React or Angular?`) with:

```markdown
The server lives in `poker-server` and an Angular client lives in `poker-client-angular`; a production build packages both into one jar.
```

Replace step 3 of `### Starting the server & registering an admin user` with:

```markdown
3. Build and run the server (from the root directory) via `./gradlew :poker-server:bootRun`. The first run downloads Node and builds the Angular client; open `http://localhost:8080/` for the UI.
```

Add a new section before `## REST Clients!`:

```markdown
## Angular client

`poker-client-angular` is the browser UI. For a fast dev loop run `npm start` inside that directory while the server runs; the Angular dev server on `http://localhost:4200` proxies API and WebSocket calls to `:8080`. See `poker-client-angular/README.md`.
```

Fix the broken spec link at the bottom so it reads:

```markdown
See [command-event-spec.md](poker-server/src/main/resources/static/command-event-spec.md) for details on the command and event specifications for client-server interactions.
```

- [ ] **Step 5: Commit**

```bash
cd ~/work/home-poker
git add CLAUDE.md README.md poker-client-angular/CLAUDE.md poker-client-angular/README.md
git commit -m "docs: describe the poker-client-angular module and combined build

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MhZV14toYwnATRwLLKG4mN"
```

---

### Task 7: End-to-end verification

**Files:** none modified.

- [ ] **Step 1: Full clean build**

```bash
cd ~/work/home-poker
./gradlew clean build 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`; the task list includes `:poker-client-angular:npmBuild`, `:poker-client-angular:npmTest`, `:poker-server:copyClientAssets`, `:poker-server:test`, `:poker-server:bootJar`.

- [ ] **Step 2: Confirm the jar contains the client**

```bash
unzip -l poker-server/build/libs/poker-server-0.0.1-SNAPSHOT.jar | grep -c "BOOT-INF/classes/static/index.html"   # expect: 1
```

- [ ] **Step 3: Run the jar and exercise the shell same-origin**

```bash
docker-compose up -d
java -jar poker-server/build/libs/poker-server-0.0.1-SNAPSHOT.jar &
sleep 8
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/            # expect: 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/home        # expect: 200
curl -s http://localhost:8080/home | grep -c "<app-root>"                  # expect: 1
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/cash-games/x  # expect: 403
kill %1
```

Then open `http://localhost:8080/` in a browser, log in, refresh on `/home`, and join a game so the WebSocket connects to `ws://localhost:8080/ws/games/...`. Confirm in the browser's network tab that the socket URL has no `:4200` and the REST calls have no `/api` prefix.

- [ ] **Step 4: Confirm the two-process dev loop still works**

```bash
./gradlew :poker-server:bootRun &        # terminal 1
cd poker-client-angular && npm start     # terminal 2
```

Open `http://localhost:4200/`, log in, and join a game. Confirm the WebSocket connects through the proxy (URL shows `ws://localhost:4200/ws/games/...`).

- [ ] **Step 5: Report**

Confirm all of the following before declaring done:

- `./gradlew clean build` passed.
- `./gradlew :poker-server:test --dry-run` lists no `npm*` or `copyClientAssets` task.
- The jar serves `/`, `/home`, and `/game/<id>` unauthenticated and still 403s API paths.
- Jest passes with the two new spec files.
- The old repo at `~/work/angular/angular-poker-client` is untouched apart from the lock-file commit; archiving or deleting it is the user's call.
