import { Component, computed, inject, input, output } from '@angular/core';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

type Day = { date: Dayjs; events: CalendarEvent[]; firstTwoEvents: CalendarEvent[]; isInDisplayedMonth: boolean; id: string };
type Week = { days: Day[]; id: string };

@Component({
    selector: 'jhi-calendar-mobile-month-presentation',
    imports: [NgClass, CalendarDayBadgeComponent, NgTemplateOutlet],
    templateUrl: './calendar-mobile-month-presentation.component.html',
    styleUrl: './calendar-mobile-month-presentation.component.scss',
})
export class CalendarMobileMonthPresentationComponent {
    private dateToEventsMap = inject(CalendarService).eventMap;

    readonly CalendarEventType = CalendarEventType;

    firstDateOfMonth = input.required<Dayjs>();
    selectDate = output<Dayjs>();

    weeks = computed<Week[]>(() => {
        const firstDateOfMonth = this.firstDateOfMonth();
        const start = firstDateOfMonth.startOf('month').startOf('isoWeek');
        const end = firstDateOfMonth.endOf('month').endOf('isoWeek');
        const weeks: Week[] = [];
        let date = start;
        while (date.isBefore(end)) {
            const days: Day[] = [];
            for (let i = 0; i < 7; i++) {
                const id = date.format('YYYY-MM-DD');
                const events = this.dateToEventsMap().get(id) ?? [];
                const firstTwoEvents = utils.limitToLengthTwo(events);
                const isInDisplayedMonth = utils.areDatesInSameMonth(date, firstDateOfMonth);
                days.push({ date: date, events, firstTwoEvents, isInDisplayedMonth, id });
                date = date.add(1, 'day');
            }
            const id = days[0].id;
            weeks.push({ days, id });
        }
        return weeks;
    });
}
