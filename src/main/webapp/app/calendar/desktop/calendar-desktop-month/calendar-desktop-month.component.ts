import { Component, OnInit } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import * as Utils from 'app/calendar/util/calendar-util';
import { DayBadgeComponent } from '../../shared/day-badge/day-badge.component';
import { CalendarEventDummyService } from '../../service/calendar-event-dummy.service';
import { CalendarEvent } from '../../entities/calendar-event.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'app-calendar',
    standalone: true,
    imports: [DayBadgeComponent, ArtemisTranslatePipe],
    templateUrl: './calendar-desktop-month.component.html',
    styleUrls: ['./calendar-desktop-month.component.scss'],
})
export class CalendarDesktopMonthComponent implements OnInit {
    readonly utils = Utils;
    firstDayOfCurrentMonth: Dayjs = dayjs().startOf('month');
    weeks: Dayjs[][] = [];

    constructor(private eventService: CalendarEventDummyService) {}
    ngOnInit(): void {
        this.generateCalendar();
    }

    getEventsOf(day: Dayjs): CalendarEvent[] {
        return this.eventService.getEventsOfDay(day);
    }

    generateCalendar(): void {
        const currentDay = dayjs();
        const startOfMonth = currentDay.startOf('month');
        const endOfMonth = currentDay.endOf('month');
        const startDay = startOfMonth.startOf('isoWeek');

        const weeks: Dayjs[][] = [];
        let current = startDay;

        while (current.isBefore(endOfMonth) || current.isSame(endOfMonth, 'day')) {
            const week: Dayjs[] = [];
            for (let i = 0; i < 7; i++) {
                week.push(current.clone());
                current = current.add(1, 'day');
            }
            weeks.push(week);
        }

        this.weeks = weeks;
    }
}
