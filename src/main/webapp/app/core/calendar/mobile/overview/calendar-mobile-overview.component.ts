import { Component, OnInit, Signal, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { distinctUntilChanged, map } from 'rxjs/operators';
import { finalize } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import dayjs, { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarMobileMonthPresentationComponent } from 'app/core/calendar/mobile/month-presentation/calendar-mobile-month-presentation.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { CalendarMobileDayPresentationComponent } from 'app/core/calendar/mobile/day-presentation/calendar-mobile-day-presentation.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight, faXmark } from '@fortawesome/free-solid-svg-icons';
import { CalendarEventFilterComponent, CalendarEventFilterComponentVariant } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-calendar-mobile-overview',
    imports: [
        CalendarMobileMonthPresentationComponent,
        CalendarMobileDayPresentationComponent,
        TranslateDirective,
        FaIconComponent,
        CalendarEventFilterComponent,
        CalendarSubscriptionPopoverComponent,
    ],
    templateUrl: './calendar-mobile-overview.component.html',
    styleUrl: './calendar-mobile-overview.component.scss',
})
export class CalendarMobileOverviewComponent implements OnInit {
    private translateService = inject(TranslateService);
    private calendarService = inject(CalendarService);
    private activatedRoute = inject(ActivatedRoute);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    readonly CalendarEventFilterComponentVariant = CalendarEventFilterComponentVariant;
    readonly faXmark = faXmark;
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;

    firstDateOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    selectedDate = signal<Dayjs | undefined>(undefined);
    weekdayNameKeys = utils.getWeekDayNameKeys();
    isLoading = signal<boolean>(false);
    calendarSubscriptionToken = this.calendarService.subscriptionToken;
    currentCourseId: Signal<number | undefined> = this.getCurrentCourseIdSignal();
    monthDescription = computed<string>(() => this.firstDateOfCurrentMonth().locale(this.currentLocale()).format('MMMM YYYY'));

    constructor() {
        effect(() => {
            if (this.currentCourseId() !== undefined) {
                this.loadEventsForCurrentMonth();
            }
        });
    }

    ngOnInit(): void {
        this.calendarService.loadSubscriptionToken().subscribe();
    }

    selectDate(date: Dayjs): void {
        this.selectedDate.set(date);
    }

    unselectDate() {
        this.selectedDate.set(undefined);
    }

    goToPrevious(): void {
        if (this.selectedDate()) {
            this.selectedDate.update((oldDate) => oldDate!.subtract(1, 'day'));
            if (!this.selectedDate()!.isSame(this.firstDateOfCurrentMonth(), 'month')) {
                this.firstDateOfCurrentMonth.update((oldDate) => oldDate.subtract(1, 'month'));
            }
        } else {
            this.firstDateOfCurrentMonth.update((oldDate) => oldDate.subtract(1, 'month'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToNext(): void {
        if (this.selectedDate()) {
            this.selectedDate.update((oldDate) => oldDate!.add(1, 'day'));
            if (!this.selectedDate()!.isSame(this.firstDateOfCurrentMonth(), 'month')) {
                this.firstDateOfCurrentMonth.update((oldDate) => oldDate.add(1, 'month'));
            }
        } else {
            this.firstDateOfCurrentMonth.update((oldDate) => oldDate.add(1, 'month'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToToday(): void {
        const today = dayjs();
        if (this.selectedDate()) {
            this.selectedDate.set(today);
            this.firstDateOfCurrentMonth.set(today.startOf('month'));
        } else {
            this.firstDateOfCurrentMonth.set(today.startOf('month'));
        }
        this.loadEventsForCurrentMonth();
    }

    private getCurrentCourseIdSignal(): Signal<number | undefined> {
        return toSignal(
            this.activatedRoute.parent!.paramMap.pipe(
                map((parameterMap) => {
                    const courseIdParameter = parameterMap.get('courseId');
                    return courseIdParameter !== null ? Number(courseIdParameter) : undefined;
                }),
                distinctUntilChanged(),
            ),
            { initialValue: undefined },
        );
    }

    private loadEventsForCurrentMonth(): void {
        const courseId = this.currentCourseId();
        if (!courseId) return;
        this.isLoading.set(true);
        this.calendarService
            .loadEventsForCurrentMonth(courseId, this.firstDateOfCurrentMonth())
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe();
    }
}
