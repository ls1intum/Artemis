import { Component, computed, input } from '@angular/core';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { Dayjs } from 'dayjs/esm';
import * as Utils from 'app/calendar/shared/util/calendar-util';
import { DayBadgeComponent } from '../../shared/day-badge/day-badge.component';
import { CalendarEventDummyService } from 'app/calendar/shared/service/calendar-event-dummy.service';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'calendar-desktop-month',
    standalone: true,
    imports: [FaIconComponent, NgbPopover, DayBadgeComponent, ArtemisTranslatePipe],
    templateUrl: './calendar-month-presentation.component.html',
    styleUrls: ['./calendar-month-presentation.component.scss'],
})
export class CalendarMonthPresentationComponent {
    firstDayOfCurrentMonth = input.required<Dayjs>();
    selectedEvent?: CalendarEvent;

    readonly utils = Utils;
    readonly faXmark = faXmark;
    readonly weeks = computed(() => this.computeWeeksFrom(this.firstDayOfCurrentMonth()));
    readonly eventMap = computed(() => this.generateEventMap(this.weeks().flat()));

    private popover?: NgbPopover;

    constructor(private eventService: CalendarEventDummyService) {}

    getEventsOf(day: Dayjs): CalendarEvent[] {
        const key = day.format('YYYY-MM-DD');
        return this.eventMap().get(key) ?? [];
    }

    openPopover(event: CalendarEvent, popover: NgbPopover) {
        this.selectedEvent = event;
        this.popover?.close();
        this.popover = popover;
        popover.open();
    }

    closePopover() {
        this.popover?.close();
        this.popover = undefined;
    }

    getTimeString(timestamp: Dayjs): string {
        return timestamp.format('HH:mm');
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

    private generateEventMap(days: Dayjs[]): Map<string, CalendarEvent[]> {
        const map = new Map<string, CalendarEvent[]>();
        for (const day of days) {
            const key = day.format('YYYY-MM-DD');
            map.set(key, this.eventService.getEventsOfDay(day));
        }
        return map;
    }
}
