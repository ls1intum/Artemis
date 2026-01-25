import { Directive, computed, inject, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';

export type CalendarMonthPresentationDay = {
    date: Dayjs;
    eventsAndMetadata: CalendarMonthPresentationEventAndMetadata[];
    firstTwoEventsAndMetadata: CalendarMonthPresentationEventAndMetadata[];
    isInDisplayedMonth: boolean;
    id: string;
};
export type CalendarMonthPresentationWeek = { days: CalendarMonthPresentationDay[]; id: string };
export type CalendarMonthPresentationEventAndMetadata = { event: CalendarEvent; icon: IconProp; color: string };

@Directive()
export class CalendarMonthPresentationComponent {
    private dateToEventsMap = inject(CalendarService).eventMap;

    firstDateOfCurrentMonth = input.required<Dayjs>();
    weeks = computed<CalendarMonthPresentationWeek[]>(() => this.computeWeeks());

    private computeWeeks(): CalendarMonthPresentationWeek[] {
        const monthStart = this.firstDateOfCurrentMonth();
        const start = monthStart.startOf('month').startOf('isoWeek');
        const end = monthStart.endOf('month').endOf('isoWeek');
        const weeks: CalendarMonthPresentationWeek[] = [];
        let date = start;
        while (date.isBefore(end)) {
            const days: CalendarMonthPresentationDay[] = [];
            for (let i = 0; i < 7; i++) {
                const id = date.format('YYYY-MM-DD');
                const calendarEvents = this.dateToEventsMap().get(id) ?? [];
                const eventsAndMetadata = calendarEvents.map((event) => {
                    return {
                        event: event,
                        icon: utils.getIconForEvent(event),
                        color: utils.getColorFor(event),
                    };
                });
                const firstTwoEventsAndMetadata = eventsAndMetadata.slice(0, 2);
                const isInDisplayedMonth = date.month() === monthStart.month();
                days.push({ date, eventsAndMetadata, firstTwoEventsAndMetadata, isInDisplayedMonth, id });
                date = date.add(1, 'day');
            }
            const id = days[0].id;
            weeks.push({ days, id });
        }
        return weeks;
    }
}
