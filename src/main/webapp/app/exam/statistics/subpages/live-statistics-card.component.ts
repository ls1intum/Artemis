import { Component, Input } from '@angular/core';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-live-statistics-box',
    templateUrl: './live-statistics-card.component.html',
    styleUrls: ['./live-statistics-card.component.scss'],
})
export class LiveStatisticsCardComponent {
    @Input() title: string;
    @Input() linkTo?: string;
    @Input() courseId?: number;
    @Input() examId?: number;
    @Input() description: string;

    faArrowRight = faArrowRight;

    constructor() {}
}
