import { Component } from '@angular/core';
import { CashTableComponent } from './cash-table/cash-table.component';
import { PlayerComponent } from './player/player.component';

@Component({
    selector: 'app-game-page',
    templateUrl: './game-page.component.html',
    styleUrl: './game-page.component.css',
    imports: [CashTableComponent, PlayerComponent]
})
export class GamePageComponent {}
