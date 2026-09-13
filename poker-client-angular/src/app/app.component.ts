import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavigationBarComponent } from './navigation-bar/navigation-bar.component';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    imports: [
        RouterOutlet,
        NavigationBarComponent,
    ]
})
export class AppComponent {}
