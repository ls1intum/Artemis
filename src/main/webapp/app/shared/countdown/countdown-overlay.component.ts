import { Component, EventEmitter, Input, Output } from '@angular/core';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-countdown-overlay',
    templateUrl: './countdown-overlay.component.html',
})
export class CountdownOverlayComponent {
    @Input() targetDate: dayjs.Dayjs;
    @Input() timeUntilPlannedStartText: string;
    @Input() waitForStartText: string;
    @Output() onFinish = new EventEmitter<void>();
}
