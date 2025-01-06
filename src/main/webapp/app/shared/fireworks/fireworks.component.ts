import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-fireworks',
    template: ` @if (active) {
        <div class="pyro">
            <div class="before"></div>
            <div class="after"></div>
        </div>
    }`,
    styleUrls: ['./fireworks.component.scss'],
    standalone: false,
})
export class FireworksComponent {
    @Input() active = false;
}
