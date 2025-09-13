import { Component, computed, inject, input, signal } from '@angular/core';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Dayjs } from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';

@Component({
    selector: 'jhi-calendar-desktop-month-presentation',
    imports: [NgClass, NgTemplateOutlet, NgbPopover, FaIconComponent, ArtemisTranslatePipe, TranslateDirective, CalendarDayBadgeComponent, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-desktop-month-presentation.component.html',
    styleUrls: ['./calendar-desktop-month-presentation.component.scss'],
})
export class CalendarDesktopMonthPresentationComponent {
    private popover?: NgbPopover;
    private eventMap = inject(CalendarService).eventMap;

    firstDayOfCurrentMonth = input.required<Dayjs>();
    selectedEvent = signal<CalendarEvent | undefined>(undefined);

    readonly utils = utils;
    readonly CalendarEventType = CalendarEventType;
    readonly weeks = computed(() => this.computeWeeksFrom(this.firstDayOfCurrentMonth()));

    getEventsOf(day: Dayjs): CalendarEvent[] {
        const key = day.format('YYYY-MM-DD');
        return this.eventMap().get(key) ?? [];
    }

    openPopover(event: CalendarEvent, popover: NgbPopover) {
        if (this.selectedEvent() === event) {
            this.closePopover();
            return;
        }
        this.selectedEvent.set(event);
        this.popover?.close();
        this.popover = popover;
        popover.open();
    }

    closePopover() {
        this.popover?.close();
        this.popover = undefined;
        this.selectedEvent.set(undefined);
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
