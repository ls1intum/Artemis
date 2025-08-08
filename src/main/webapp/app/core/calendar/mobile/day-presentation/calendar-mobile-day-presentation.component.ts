import { AfterViewInit, Component, ElementRef, input, signal, viewChild } from '@angular/core';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';

@Component({
    selector: 'calendar-mobile-day-presentation',
    imports: [CalendarDayBadgeComponent, CalendarEventsPerDaySectionComponent],
    templateUrl: './calendar-mobile-day-presentation.component.html',
    styleUrl: './calendar-mobile-day-presentation.component.scss',
})
export class CalendarMobileDayPresentationComponent implements AfterViewInit {
    readonly utils = utils;
    private scrollContainer = viewChild<ElementRef>('scrollContainer');

    selectedDay = input.required<Dayjs>();
    isEventSelected = signal<boolean>(false);

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = 7.5 * CalendarEventsPerDaySectionComponent.HOUR_SEGMENT_HEIGHT_IN_PIXEL;
        }
    }

    isSelected(day: Dayjs): boolean {
        return day.isSame(this.selectedDay(), 'day');
    }
}
