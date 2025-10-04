import { Component, computed, inject, input } from '@angular/core';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Dayjs } from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover-component/calendar-event-detail-popover.component';

@Component({
    selector: 'jhi-calendar-desktop-month-presentation',
    imports: [NgClass, NgTemplateOutlet, FaIconComponent, TranslateDirective, CalendarDayBadgeComponent, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-desktop-month-presentation.component.html',
    styleUrls: ['./calendar-desktop-month-presentation.component.scss'],
})
export class CalendarDesktopMonthPresentationComponent {
    private eventMap = inject(CalendarService).eventMap;

    readonly utils = utils;
    readonly CalendarEventType = CalendarEventType;

    firstDayOfCurrentMonth = input.required<Dayjs>();
    weeks = computed(() => this.computeWeeksFrom(this.firstDayOfCurrentMonth()));

    getEventsOf(day: Dayjs): CalendarEvent[] {
        const key = day.format('YYYY-MM-DD');
        return this.eventMap().get(key) ?? [];
    }

    /**
     * Computes all weeks overlapping with the month of the parameter.
     *
     * @param firstDayOfSomeMonth - The first day of some month.
     * @returns An array of weeks, where each week is an array of 7 Dayjs dates.
     */
    private computeWeeksFrom(firstDayOfSomeMonth: Dayjs): Dayjs[][] {
        const startOfMonth = firstDayOfSomeMonth;
        const endOfMonth = startOfMonth.endOf('month');
        const startDay = startOfMonth.startOf('isoWeek');

        const calendar: Dayjs[][] = [];
        let current = startDay;

        while (!current.isAfter(endOfMonth)) {
            calendar.push(utils.getDatesInWeekOf(current));
            current = current.add(7, 'day');
        }

        return calendar;
    }
}
