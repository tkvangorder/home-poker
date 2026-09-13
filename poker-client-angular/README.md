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
