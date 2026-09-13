import {
  Component,
  ElementRef,
  HostListener,
  OnInit,
  ViewChild,
} from '@angular/core';
import { UserService } from '../user/user-service';
import { Observable, map } from 'rxjs';
import { AsyncPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ToastDisplayComponent } from '../toaster/toast-display.component';
import { ModalService } from '../modal/modal.service';
import { AboutComponent } from './about/about/about.component';

@Component({
  selector: 'app-navigation-bar',
  templateUrl: './navigation-bar.component.html',
  imports: [AsyncPipe, RouterLink, ToastDisplayComponent],
})
export class NavigationBarComponent implements OnInit {
  private static readonly THEME_KEY = 'selected-theme';

  @ViewChild('profileDropDown')
  profileDropDown!: ElementRef;

  @ViewChild('themeDropDown')
  themeDropDown!: ElementRef;

  constructor(
    public userService: UserService,
    private modalService: ModalService,
  ) {}

  ngOnInit() {
    const theme = localStorage.getItem(NavigationBarComponent.THEME_KEY);
    if (theme) {
      document.documentElement.setAttribute('data-theme', theme);
    }
  }

  userGreetings(): Observable<string> {
    return this.userService.observeCurrentUser().pipe(
      map((user) => {
        if (user) {
          return user.alias || user.name || user.id || '';
        } else {
          return '';
        }
      }),
    );
  }

  isAdmin$: Observable<boolean> = this.userService
    .observeCurrentUser()
    .pipe(map((user) => user?.roles?.includes('ADMIN') ?? false));

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (
      this.profileDropDown &&
      !this.profileDropDown.nativeElement.contains(event.target)
    ) {
      this.profileDropDown.nativeElement.open = false;
    }
    if (
      this.themeDropDown &&
      !this.themeDropDown.nativeElement.contains(event.target)
    ) {
      this.themeDropDown.nativeElement.open = false;
    }
  }

  closeProfileDropdown() {
    this.profileDropDown.nativeElement.open = false;
  }

  selectTheme(theme: string) {
    this.themeDropDown.nativeElement.open = false;
    localStorage.setItem(NavigationBarComponent.THEME_KEY, theme);
  }

  openAbout() {
    this.closeProfileDropdown();
    this.modalService.openDialog(AboutComponent);
  }

  logout() {
    this.closeProfileDropdown();
    this.userService.logout();
  }
}
