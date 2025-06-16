import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'day-badge',
    standalone: true,
    imports: [NgClass],
    templateUrl: './day-badge.component.html',
    styleUrls: ['./day-badge.component.scss'],
})
export class DayBadgeComponent {
    @Input({ required: true }) date!: dayjs.Dayjs;
    @Input() minimalTodayIndication: boolean = false;
    @Input() selected: boolean = false;

    get day(): number {
        return this.date.date();
    }

    isToday(): boolean {
        return this.date.isSame(dayjs(), 'day');
    }
}
