import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { formatDate } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import {
  Modal,
  ModalComponent,
  ModalOptions,
} from '../../../modal/modal.component';
import { ModalService } from '../../../modal/modal.service';
import { UserService } from '../../../user/user-service';
import { ValidationError } from '../../../error-handling/error-models';
import { CashGameService } from '../../../game/cash-game.service';
import { GameType } from '../../../game/game-models';
import { CalendarUtils } from '../../../lib/calendar-utils';
import { LangUtils } from '../../../lib/lang.utils';

@Component({
    selector: 'app-create-cash-game-dialog',
    templateUrl: './create-cash-game-dialog.component.html',
    imports: [ModalComponent, ReactiveFormsModule]
})
export class CreateCashGameDialogComponent implements Modal {
  modalOptions: ModalOptions = {
    id: 'create-cash-game',
    title: 'Create Cash Game',
    buttons: [
      { label: 'Create Game', type: 'submit' },
      { label: 'Cancel', type: 'cancel' },
    ],
  };

  gameConfigurationForm: FormGroup;

  today: string;
  now: string;

  public error: string | undefined;

  constructor(
    private modalService: ModalService,
    private router: Router,
    private userService: UserService,
    private cashGameService: CashGameService
  ) {
    const currentDate = new Date();

    this.today = formatDate(currentDate, 'yyyy-MM-dd', 'en');
    this.now = formatDate(currentDate, 'HH:mm', 'en');
    this.gameConfigurationForm = new FormGroup({
      name: new FormControl(''),
      gameType: new FormControl("Texas Hold'em"),
      startDate: new FormControl(this.today),
      startTime: new FormControl(this.now),
      maxBuyIn: new FormControl<number>(60.0),
      smallBlind: new FormControl<number>(0.25),
      bigBlind: new FormControl<number>(0.5),
    });
  }

  createGame() {
    const gameDetails = this.cashGameService
      .createGame({
        name: this.gameConfigurationForm.value.name ?? undefined,
        gameType: this.stringToType(
          this.gameConfigurationForm.value.gameType ?? undefined
        ),
        startTime: CalendarUtils.combineDateTime(
          this.gameConfigurationForm.value.startDate ?? undefined,
          this.gameConfigurationForm.value.startTime ?? undefined
        ),
        maxBuyIn: LangUtils.asCents(this.gameConfigurationForm.value.maxBuyIn),
        smallBlind: LangUtils.asCents(
          this.gameConfigurationForm.value.smallBlind
        ),
        bigBlind: LangUtils.asCents(this.gameConfigurationForm.value.bigBlind),
      })
      .pipe(
        catchError((error: ValidationError) => {
          this.error = error.message;
          return throwError(() => error);
        })
      )
      .subscribe({
        next: (user) => {
          this.modalService.close(this);
        },
      });
  }

  formatCurrency(event: FocusEvent) {
    const inputElement = event.target as HTMLInputElement;
    inputElement.value = LangUtils.parseCurrency(inputElement.value);
  }

  stringToType(gameType: string | undefined): GameType {
    switch (gameType) {
      case "Texas Hold'em":
        return GameType.TEXAS_HOLDEM;
      default:
        return GameType.TEXAS_HOLDEM;
    }
  }
}
