import { AfterViewInit, Component, ElementRef, computed, input, viewChild } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';

type Day = { date: Dayjs; weekdayNameKey: string; id: string };

@Component({
    selector: 'jhi-calendar-desktop-week-presentation',
    imports: [CalendarDayBadgeComponent, ArtemisTranslatePipe, CalendarEventsPerDaySectionComponent],
    templateUrl: './calendar-desktop-week-presentation.component.html',
    styleUrl: './calendar-desktop-week-presentation.component.scss',
})
export class CalendarDesktopWeekPresentationComponent implements AfterViewInit {
    private static readonly INITIAL_SCROLL_HOURS_AFTER_MIDNIGHT = 7.5;
    private static readonly INITIAL_SCROLL_POSITION =
        CalendarDesktopWeekPresentationComponent.INITIAL_SCROLL_HOURS_AFTER_MIDNIGHT * CalendarEventsPerDaySectionComponent.HOUR_HEIGHT_IN_PIXEL;

    firstDayOfCurrentWeek = input.required<Dayjs>();
    scrollContainer = viewChild<ElementRef>('scrollContainer');
    weekdays = computed<Day[]>(() => this.computeWeekdaysFrom(this.firstDayOfCurrentWeek()));
    dates = computed<Dayjs[]>(() => this.weekdays().map((day) => day.date));

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = CalendarDesktopWeekPresentationComponent.INITIAL_SCROLL_POSITION;
        }
    }

    private computeWeekdaysFrom(firstDayOfWeek: Dayjs): Day[] {
        return Array.from({ length: 7 }, (_, index) => {
            const date = firstDayOfWeek.add(index, 'day');
            const weekdayNameKey = this.getWeekdayNameKey(date);
            const id = date.format('YYYY-MM-DD');
            return { date, weekdayNameKey, id };
        });
    }

    private getWeekdayNameKey(day: Dayjs): string {
        const keys = utils.getWeekdayNameKeys();
        return keys[day.isoWeekday() - 1];
    }
}
