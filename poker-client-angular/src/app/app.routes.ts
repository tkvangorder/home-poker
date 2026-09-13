import { Routes } from '@angular/router';
import { TitlePageComponent } from './title-page/title-page.component';
import { HomePageComponent } from './home-page/home-page.component';
import { GameLobbyComponent } from './game-lobby/game-lobby.component';
import { authenticationGuard, loggedInGuard } from './auth-guard.service';

// When adding a top-level route here, also add it to
// poker-server/src/main/java/org/homepoker/rest/SpaForwardingController.java
// (so a browser refresh on the route serves index.html from the packaged jar)
// and to the GET permitAll list in
// poker-server/src/main/java/org/homepoker/security/WebSecurityConfiguration.java.
// `ng serve` will not reveal a missing entry because the dev server serves the
// shell for every path.
export const routes: Routes = [
  {
    path: '',
    component: TitlePageComponent,
    title: 'Chico Degens Poker Club',
    canActivate: [loggedInGuard]
  },
  {
    path: 'home',
    component: HomePageComponent,
    title: 'Chico Degens Poker Club',
    canActivate: [authenticationGuard]
  },
  {
    path: 'game/:gameId',
    component: GameLobbyComponent,
    title: 'Chico Degens Poker Club - Game Lobby',
    canActivate: [authenticationGuard]
  }
];
