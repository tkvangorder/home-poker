import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ModalComponent } from '../modal/modal.component';
import { UserService } from '../user/user-service';
import { LoginDialogComponent } from './login-dialog/login-dialog.component';
import { RegisterUserFormComponent } from './register-user-dialog/register-user-dialog.component';
import { ModalService } from '../modal/modal.service';

@Component({
    selector: 'app-title-page',
    templateUrl: './title-page.component.html',
    imports: [RouterLink]
})
export class TitlePageComponent {
  constructor(
    private userService: UserService,
    private modalService: ModalService
  ) {}

  openLoginModal() {
    this.modalService.openDialog(LoginDialogComponent);
    //this.modalService.open('login');
  }
  openRegisterUserModal() {
    this.modalService.openDialog(RegisterUserFormComponent);
  }
}
