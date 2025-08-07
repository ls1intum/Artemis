import { Component, signal } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarMobileMonthPresentation } from 'app/core/calendar/mobile/month-presentation/calendar-mobile-month-presentation.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarMobileDayPresentation } from 'app/core/calendar/mobile/day-presentation/calendar-mobile-day-presentation';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight, faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { CalendarEventFilterComponent, CalendarEventFilterComponentVariant } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';

@Component({
    selector: 'calendar-mobile-overview',
    imports: [NgStyle, NgClass, CalendarMobileMonthPresentation, CalendarMobileDayPresentation, TranslateDirective, FaIconComponent, NgbPopover, CalendarEventFilterComponent],
    templateUrl: './calendar-mobile-overview.html',
    styleUrl: './calendar-mobile-overview.scss',
})
export class CalendarMobileOverviewComponent {
    readonly CalendarEventFilterComponentVariant = CalendarEventFilterComponentVariant;
    readonly utils = utils;
    readonly faXmark = faXmark;
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;

    firstDayOfSelectedMonth = signal<Dayjs>(dayjs().startOf('month'));
    selectedDay = signal<Dayjs | undefined>(undefined);

    selectDay(day: Dayjs): void {
        this.selectedDay.set(day);
    }

    unselectDay() {
        this.selectedDay.set(undefined);
    }

    goToPrevious(): void {
        if (this.selectedDay()) {
            this.selectedDay.update((oldDay) => oldDay!.subtract(1, 'day'));
            if (!this.selectedDay()!.isSame(this.firstDayOfSelectedMonth(), 'month')) {
                this.firstDayOfSelectedMonth.update((oldDay) => oldDay.subtract(1, 'month'));
            }
        } else {
            this.firstDayOfSelectedMonth.update((oldDay) => oldDay.subtract(1, 'month'));
        }
        //this.loadEventsForCurrentMonth();
    }

    goToNext(): void {
        if (this.selectedDay()) {
            this.selectedDay.update((oldDay) => oldDay!.add(1, 'day'));
            if (!this.selectedDay()!.isSame(this.firstDayOfSelectedMonth(), 'month')) {
                this.firstDayOfSelectedMonth.update((oldDay) => oldDay.add(1, 'month'));
            }
        } else {
            this.firstDayOfSelectedMonth.update((oldDay) => oldDay.add(1, 'month'));
        }
        //this.loadEventsForCurrentMonth();
    }

    goToToday(): void {
        const today = dayjs();
        if (this.selectedDay()) {
            this.selectedDay.set(today);
            this.firstDayOfSelectedMonth.set(today.startOf('month'));
        } else {
            this.firstDayOfSelectedMonth.set(today.startOf('month'));
        }
        //this.loadEventsForCurrentMonth();
    }
}
