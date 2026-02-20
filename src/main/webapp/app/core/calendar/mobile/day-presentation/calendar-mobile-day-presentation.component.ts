import { AfterViewInit, Component, ElementRef, computed, input, viewChild } from '@angular/core';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { Dayjs } from 'dayjs/esm';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';

type Day = { date: Dayjs; isSelected: boolean; id: string };

@Component({
    selector: 'jhi-calendar-mobile-day-presentation',
    imports: [CalendarDayBadgeComponent, CalendarEventsPerDaySectionComponent],
    templateUrl: './calendar-mobile-day-presentation.component.html',
    styleUrl: './calendar-mobile-day-presentation.component.scss',
})
export class CalendarMobileDayPresentationComponent implements AfterViewInit {
    private static readonly INITIAL_SCROLL_HOURS_AFTER_MIDNIGHT = 7.5;
    private static readonly INITIAL_SCROLL_POSITION =
        CalendarMobileDayPresentationComponent.INITIAL_SCROLL_HOURS_AFTER_MIDNIGHT * CalendarEventsPerDaySectionComponent.HOUR_HEIGHT_IN_PIXEL;
    private scrollContainer = viewChild<ElementRef>('scrollContainer');

    date = input.required<Dayjs>();
    weekdays = computed<Day[]>(() => this.computeWeekdaysFor(this.date()));

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = CalendarMobileDayPresentationComponent.INITIAL_SCROLL_POSITION;
        }
    }

    private computeWeekdaysFor(date: Dayjs): Day[] {
        const selectedDate = this.date();
        const dates = this.getDatesInWeekOf(date);
        return dates.map((date) => ({ date: date, isSelected: date.isSame(selectedDate, 'day'), id: date.format('YYYY-MM-DD') }));
    }

    private getDatesInWeekOf(date: Dayjs): Dayjs[] {
        const start = date.startOf('isoWeek');
        const week: Dayjs[] = [];
        let currentDay = start;
        for (let i = 0; i < 7; i++) {
            week.push(currentDay.clone());
            currentDay = currentDay.add(1, 'day');
        }
        return week;
    }
}
