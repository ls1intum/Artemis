import { Component, input, signal } from '@angular/core';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';

@Component({
    selector: 'calendar-mobile-day-presentation',
    imports: [CalendarDayBadgeComponent, CalendarEventsPerDaySectionComponent],
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
