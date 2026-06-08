import { Component, computed, input } from '@angular/core';
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
    date = input.required<Dayjs>();
    minimalTodayIndication = input(false);
    isSelectedDay = input(false);
    dayNumber = computed<number>(() => this.date().date());
    isToday = computed<boolean>(() => this.date().isSame(dayjs(), 'day'));
}
