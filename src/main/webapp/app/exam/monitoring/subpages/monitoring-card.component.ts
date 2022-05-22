import { Component, Input } from '@angular/core';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';

@Component({
    selector: 'jhi-monitoring-box',
    templateUrl: './monitoring-card.component.html',
    styleUrls: ['./monitoring-card.component.scss'],
})
export class MonitoringCardComponent {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    @Input() title: string;
    @Input() description: string;
    @Input() color: string;

    constructor() {}
}
