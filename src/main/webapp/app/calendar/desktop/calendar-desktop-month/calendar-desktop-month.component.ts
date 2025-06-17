import { Component, computed, input } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import * as Utils from 'app/calendar/util/calendar-util';
import { DayBadgeComponent } from '../../shared/day-badge/day-badge.component';
import { CalendarEventDummyService } from '../../service/calendar-event-dummy.service';
import { CalendarEvent } from '../../entities/calendar-event.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'calendar-desktop-month',
    standalone: true,
    imports: [DayBadgeComponent, ArtemisTranslatePipe],
    templateUrl: './calendar-desktop-month.component.html',
    styleUrls: ['./calendar-desktop-month.component.scss'],
})
export class CalendarDesktopMonthComponent {
    readonly utils = Utils;
    firstDayOfCurrentMonth = input.required<Dayjs>();

    readonly weeks = computed(() => this.computeWeeksFrom(this.firstDayOfCurrentMonth()));
    readonly eventMap = computed(() => this.generateEventMap(this.weeks().flat()));

    constructor(private eventService: CalendarEventDummyService) {}

    getEventsOf(day: Dayjs): CalendarEvent[] {
        const key = day.format('YYYY-MM-DD');
        return this.eventMap().get(key) ?? [];
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
