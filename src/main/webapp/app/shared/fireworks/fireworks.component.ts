import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-fireworks',
    template: ` <div class="pyro" *ngIf="active">
        <div class="before"></div>
        <div class="after"></div>
    </div>`,
    styleUrls: ['./fireworks.component.scss'],
})
export class FireworksComponent {
    @Input() active = false;
}
