import { Component, input, output } from '@angular/core';
import { NgClass } from '@angular/common';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'calendar-mobile-month-section',
    imports: [NgClass, CalendarDayBadgeComponent, TranslateDirective],
    templateUrl: './calendar-mobile-month-section.html',
    styleUrl: './calendar-mobile-month-section.scss',
})
export class CalendarMobileMonthSection {
    firstDayOfMonth = input.required<Dayjs>();
    isDayAndWeekSelected = input.required<boolean>();
    selectDay = output<Dayjs>();
    unselectDay = output<void>();

    readonly utils = utils;

    getEventsOfDay(day: Dayjs): CalendarEvent[] {
        return [];
    }
}
