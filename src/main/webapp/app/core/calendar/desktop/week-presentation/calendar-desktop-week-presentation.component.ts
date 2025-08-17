import { AfterViewInit, Component, ElementRef, computed, input, signal, viewChild } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { Dayjs } from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';
import { CalendarOverviewComponent } from 'app/core/calendar/calendar-overview/calendar-overview.component';

@Component({
    selector: 'jhi-calendar-desktop-week-presentation',
    imports: [CalendarDayBadgeComponent, ArtemisTranslatePipe, NgbPopover, NgStyle, NgClass, CalendarEventsPerDaySectionComponent],
    templateUrl: './calendar-desktop-week-presentation.component.html',
    styleUrl: './calendar-desktop-week-presentation.component.scss',
})
export class CalendarDesktopWeekPresentationComponent implements AfterViewInit {
    firstDayOfCurrentWeek = input.required<Dayjs>();
    isEventSelected = signal<boolean>(false);
    scrollContainer = viewChild<ElementRef>('scrollContainer');
    weekDays = computed(() => this.computeWeekDaysFrom(this.firstDayOfCurrentWeek()));

    readonly utils = utils;

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = CalendarOverviewComponent.INITIAL_SCROLL_POSITION;
        }
    }

    private computeWeekDaysFrom(firstDayOfWeek: Dayjs): Dayjs[] {
        return Array.from({ length: 7 }, (_, index) => firstDayOfWeek.add(index, 'day'));
    }
}
