import { Component, input, output } from '@angular/core';
import { NgClass } from '@angular/common';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'calendar-mobile-month-presentation',
    imports: [NgClass, CalendarDayBadgeComponent, TranslateDirective],
    templateUrl: './calendar-mobile-month-presentation.component.html',
    styleUrl: './calendar-mobile-month-presentation.component.scss',
})
export class CalendarMobileMonthPresentation {
    firstDayOfMonth = input.required<Dayjs>();
    selectDay = output<Dayjs>();

    readonly utils = utils;

    getEventsOfDay(day: Dayjs): CalendarEvent[] {
        return [];
    }
}
