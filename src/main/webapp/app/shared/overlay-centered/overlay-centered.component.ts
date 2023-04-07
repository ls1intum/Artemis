import { Component, HostBinding } from '@angular/core';

@Component({
    selector: 'jhi-overlay-centered',
    templateUrl: './overlay-centered.component.html',
    styleUrls: ['./overlay-centered.component.scss'],
})
export class OverlayCenteredComponent {
    @HostBinding('class') class = 'alert alert-info';
}
