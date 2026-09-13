import { Component, Input } from '@angular/core';
import {
  Modal,
  ModalComponent,
  ModalOptions,
} from '../../modal/modal.component';
import { ModalService } from '../../modal/modal.service';

import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UserService } from '../../user/user-service';
import { Router } from '@angular/router';
import { catchError, of, throwError } from 'rxjs';
import { ValidationError } from '../../error-handling/error-models';
@Component({
    selector: 'app-login-form',
    templateUrl: './login-dialog.component.html',
    imports: [ModalComponent, ReactiveFormsModule]
})
export class LoginDialogComponent implements Modal {
  loginForm = new FormGroup({
    username: new FormControl(''),
    password: new FormControl(''),
  });

  modalOptions: ModalOptions = {
    id: 'login',
    title: 'Login',
    buttons: [
      { label: 'Login', type: 'submit' },
      { label: 'Cancel', type: 'cancel' },
    ],
  };

  public error: string | undefined;
  constructor(
    private modalService: ModalService,
    private router: Router,
    private userService: UserService
  ) {}

  userLogin() {
    this.userService
      .login(
        this.loginForm.value.username ?? '',
        this.loginForm.value.password ?? ''
      )
      .pipe(
        catchError((error: ValidationError) => {
          this.error = error.message;
          return throwError(() => error);
        })
      )
      .subscribe({
        next: (user) => {
          this.modalService.close(this);
          this.router.navigate(['/home']);
        },
      });
  }
}
