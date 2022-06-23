import { Component, Input } from '@angular/core';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-monitoring-box',
    templateUrl: './monitoring-card.component.html',
    styleUrls: ['./monitoring-card.component.scss'],
})
export class MonitoringCardComponent {
    @Input() title: string;
    @Input() linkTo: string;
    @Input() courseId: number;
    @Input() examId: number;
    @Input() description: string;

    faArrowRight = faArrowRight;

    constructor() {}
}
