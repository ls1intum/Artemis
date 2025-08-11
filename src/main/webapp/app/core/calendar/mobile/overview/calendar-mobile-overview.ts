import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { NgClass, NgStyle } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarMobileMonthPresentationComponent } from 'app/core/calendar/mobile/month-presentation/calendar-mobile-month-presentation.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarMobileDayPresentationComponent } from 'app/core/calendar/mobile/day-presentation/calendar-mobile-day-presentation.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight, faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { CalendarEventFilterComponent, CalendarEventFilterComponentVariant } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';

@Component({
    selector: 'calendar-mobile-overview',
    imports: [
        NgStyle,
        NgClass,
        CalendarMobileMonthPresentationComponent,
        CalendarMobileDayPresentationComponent,
        TranslateDirective,
        FaIconComponent,
        NgbPopover,
        CalendarEventFilterComponent,
    ],
    templateUrl: './calendar-mobile-overview.html',
    styleUrl: './calendar-mobile-overview.scss',
})
export class CalendarMobileOverviewComponent implements OnInit, OnDestroy {
    private calendarEventService = inject(CalendarEventService);
    private activatedRoute = inject(ActivatedRoute);
    private activatedRouteSubscription?: Subscription;
    private courseId?: number;

    readonly CalendarEventFilterComponentVariant = CalendarEventFilterComponentVariant;
    readonly utils = utils;
    readonly faXmark = faXmark;
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;

    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    selectedDay = signal<Dayjs | undefined>(undefined);

    ngOnInit(): void {
        this.activatedRouteSubscription = this.activatedRoute.parent?.paramMap.subscribe((parameterMap) => {
            const courseIdParameter = parameterMap.get('courseId');
            if (courseIdParameter) {
                this.courseId = +courseIdParameter;
                this.loadEventsForCurrentMonth();
            }
        });
    }

    ngOnDestroy() {
        this.activatedRouteSubscription?.unsubscribe();
    }

    selectDay(day: Dayjs): void {
        this.selectedDay.set(day);
    }

    unselectDay() {
        this.selectedDay.set(undefined);
    }

    goToPrevious(): void {
        if (this.selectedDay()) {
            this.selectedDay.update((oldDay) => oldDay!.subtract(1, 'day'));
            if (!this.selectedDay()!.isSame(this.firstDayOfCurrentMonth(), 'month')) {
                this.firstDayOfCurrentMonth.update((oldDay) => oldDay.subtract(1, 'month'));
            }
        } else {
            this.firstDayOfCurrentMonth.update((oldDay) => oldDay.subtract(1, 'month'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToNext(): void {
        if (this.selectedDay()) {
            this.selectedDay.update((oldDay) => oldDay!.add(1, 'day'));
            if (!this.selectedDay()!.isSame(this.firstDayOfCurrentMonth(), 'month')) {
                this.firstDayOfCurrentMonth.update((oldDay) => oldDay.add(1, 'month'));
            }
        } else {
            this.firstDayOfCurrentMonth.update((oldDay) => oldDay.add(1, 'month'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToToday(): void {
        const today = dayjs();
        if (this.selectedDay()) {
            this.selectedDay.set(today);
            this.firstDayOfCurrentMonth.set(today.startOf('month'));
        } else {
            this.firstDayOfCurrentMonth.set(today.startOf('month'));
        }
        this.loadEventsForCurrentMonth();
    }

    private loadEventsForCurrentMonth(): void {
        if (!this.courseId) return;
        this.calendarEventService.loadEventsForCurrentMonth(this.courseId, this.firstDayOfCurrentMonth()).subscribe();
    }
}
