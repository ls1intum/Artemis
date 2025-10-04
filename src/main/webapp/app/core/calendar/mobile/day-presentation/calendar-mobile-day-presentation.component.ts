import { AfterViewInit, Component, ElementRef, computed, input, viewChild } from '@angular/core';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
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
    readonly utils = utils;
    private scrollContainer = viewChild<ElementRef>('scrollContainer');

    selectedDate = input.required<Dayjs>();
    weekDays = computed<Day[]>(() => {
        const selectedDate = this.selectedDate();
        const dates = utils.getDatesInWeekOf(selectedDate);
        return dates.map((date) => ({ date: date, isSelected: date.isSame(selectedDate, 'day'), id: utils.identify(date) }));
    });

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = CalendarMobileDayPresentationComponent.INITIAL_SCROLL_POSITION;
        }
    }
}
