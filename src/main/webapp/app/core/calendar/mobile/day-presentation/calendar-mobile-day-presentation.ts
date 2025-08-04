import { Component, input, signal } from '@angular/core';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEventsPerDayPresentation } from 'app/core/calendar/shared/calendar-events-per-day-presentation/calendar-events-per-day-presentation.component';

@Component({
    selector: 'calendar-mobile-day-presentation',
    imports: [CalendarDayBadgeComponent, CalendarEventsPerDayPresentation],
    templateUrl: './calendar-mobile-day-presentation.html',
    styleUrl: './calendar-mobile-day-presentation.scss',
})
export class CalendarMobileDayPresentation {
    readonly utils = utils;

    selectedDay = input.required<Dayjs>();
    isEventSelected = signal<boolean>(false);

    isSelected(day: Dayjs): boolean {
        return day.isSame(this.selectedDay(), 'day');
    }
}
