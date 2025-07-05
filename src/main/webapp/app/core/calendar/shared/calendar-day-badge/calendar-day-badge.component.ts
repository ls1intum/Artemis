import { Component, InputSignal, input } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'day-badge',
    standalone: true,
    imports: [NgClass],
    templateUrl: './calendar-day-badge.component.html',
    styleUrls: ['./calendar-day-badge.component.scss'],
})
export class CalendarDayBadgeComponent {
    day: InputSignal<Dayjs> = input.required<Dayjs>();

    get dayNumber(): number {
        return this.day().date();
    }

    isToday(): boolean {
        return this.day().isSame(dayjs(), 'day');
    }
}
