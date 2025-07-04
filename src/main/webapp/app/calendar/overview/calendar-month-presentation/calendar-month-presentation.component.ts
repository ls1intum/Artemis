import { Component, computed, input, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Dayjs } from 'dayjs/esm';
import * as Utils from 'app/calendar/shared/util/calendar-util';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarEventService } from 'app/calendar/shared/service/calendar-event.service';
import { CalendarEventDetailPopoverComponent } from 'app/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';
import { CalendarDayBadgeComponent } from 'app/calendar/shared/calendar-day-badge/calendar-day-badge.component';

@Component({
    selector: 'calendar-desktop-month',
    standalone: true,
    imports: [NgClass, FaIconComponent, NgbPopover, CalendarDayBadgeComponent, CalendarEventDetailPopoverComponent, ArtemisTranslatePipe],
    templateUrl: './calendar-month-presentation.component.html',
    styleUrls: ['./calendar-month-presentation.component.scss'],
})
export class CalendarMonthPresentationComponent {
    firstDayOfCurrentMonth = input.required<Dayjs>();
    selectedEvent = signal<CalendarEvent | undefined>(undefined);

    readonly utils = Utils;
    readonly weeks = computed(() => this.computeWeeksFrom(this.firstDayOfCurrentMonth()));
    readonly eventMap;

    private popover?: NgbPopover;

    constructor(private eventService: CalendarEventService) {
        this.eventMap = this.eventService.eventMap;
    }

    getEventsOf(day: Dayjs): CalendarEvent[] {
        const key = day.format('YYYY-MM-DD');
        const result = this.eventMap().get(key) ?? [];
        return result;
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

    private computeWeeksFrom(startDate: Dayjs): Dayjs[][] {
        const startOfMonth = startDate.startOf('month');
        const endOfMonth = startOfMonth.endOf('month');
        const startDay = startOfMonth.startOf('isoWeek');

        const calendar: Dayjs[][] = [];
        let current = startDay;

        while (current.isBefore(endOfMonth) || current.isSame(endOfMonth, 'day')) {
            const week: Dayjs[] = [];
            for (let i = 0; i < 7; i++) {
                week.push(current.clone());
                current = current.add(1, 'day');
            }
            calendar.push(week);
        }

        return calendar;
    }
}
