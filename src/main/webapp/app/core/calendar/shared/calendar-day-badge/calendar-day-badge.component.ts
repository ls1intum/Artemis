import { Component, input } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-calendar-day-badge',
    standalone: true,
    imports: [NgClass],
    templateUrl: './calendar-day-badge.component.html',
    styleUrls: ['./calendar-day-badge.component.scss'],
})
export class CalendarDayBadgeComponent {
    day = input.required<Dayjs>();
    minimalTodayIndication = input(false);
    isSelectedDay = input(false);

    get dayNumber(): number {
        return this.day().date();
    }

    isToday(): boolean {
        return this.day().isSame(dayjs(), 'day');
    }
}
