import { Component, inject, OnInit } from '@angular/core';
import { CashGameDetailsComponent } from './cash-game-details/cash-game-details/cash-game-details.component';
import { CashGameListComponent } from './cash-game-list/cash-game-list/cash-game-list.component';
import { UserService } from '../user/user-service';
import { ModalService } from '../modal/modal.service';
import { CreateCashGameDialogComponent } from './create-cash-game-dialog/create-cash-game-dialog/create-cash-game-dialog.component';
import { CashGameService } from '../game/cash-game.service';

@Component({
  selector: 'app-home-page',
  templateUrl: './home-page.component.html',
  imports: [CashGameDetailsComponent, CashGameListComponent],
})
export class HomePageComponent implements OnInit {
  private userService = inject(UserService);
  private modalService = inject(ModalService);
  private cashGameService = inject(CashGameService);

  showCompleted = false;

  get isAdmin(): boolean {
    const user = this.userService.getCurrentUser();
    return user ? this.userService.isAdmin(user) : false;
  }

  ngOnInit(): void {
    this.cashGameService.loadGames(this.showCompleted);
  }

  toggleShowCompleted(): void {
    this.showCompleted = !this.showCompleted;
    this.cashGameService.loadGames(this.showCompleted);
  }

  openCreateCashGameModal() {
    this.modalService.openDialog(CreateCashGameDialogComponent);
  }
}
