import { Component } from '@angular/core';
import { CardComponent } from '../../poker/card/card.component';
import { PlayerComponent } from '../player/player.component';

@Component({
    selector: 'app-cash-table',
    templateUrl: './cash-table.component.html',
    styleUrl: './cash-table.component.css',
    imports: [CardComponent, PlayerComponent]
})
export class CashTableComponent {}
