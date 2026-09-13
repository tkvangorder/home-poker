import { Component } from '@angular/core';
import { ModalComponent, ModalOptions } from '../../modal/modal.component';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { ModalService } from '../../modal/modal.service';
import { UserService } from '../../user/user-service';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ValidationError } from '../../error-handling/error-models';

@Component({
  selector: 'app-register-user',
  templateUrl: './register-user-dialog.component.html',
  imports: [ModalComponent, ReactiveFormsModule]
})
export class RegisterUserFormComponent {
  modalOptions: ModalOptions = {
    id: 'register-user',
    title: 'Register User',
    buttons: [
      { label: 'Register User', type: 'submit' },
      { label: 'Cancel', type: 'cancel' },
    ],
  };
  registerForm = new FormGroup({
    username: new FormControl(''),
    password: new FormControl(''),
    confirmPassword: new FormControl(''),
    name: new FormControl(''),
    email: new FormControl(''),
    phone: new FormControl(''),
    passcode: new FormControl(''),
  });

  public error: string | undefined;

  constructor(
    private modalService: ModalService,
    private router: Router,
    private userService: UserService
  ) {}

  registerUser() {
    this.userService
      .registerUser({
        loginId: this.registerForm.value.username ?? undefined,
        password: this.registerForm.value.password ?? undefined,
        email: this.registerForm.value.email ?? undefined,
        name: this.registerForm.value.name ?? undefined,
        alias: this.registerForm.value.name ?? undefined,
        phone: this.registerForm.value.phone ?? undefined,
        serverPasscode: this.registerForm.value.passcode ?? undefined,
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
          this.router.navigate(['/home']);
        },
      });
  }
}
