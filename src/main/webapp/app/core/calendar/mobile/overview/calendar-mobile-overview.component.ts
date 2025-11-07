import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import dayjs, { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarMobileMonthPresentationComponent } from 'app/core/calendar/mobile/month-presentation/calendar-mobile-month-presentation.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarMobileDayPresentationComponent } from 'app/core/calendar/mobile/day-presentation/calendar-mobile-day-presentation.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpFromBracket, faChevronLeft, faChevronRight, faFilter, faXmark } from '@fortawesome/free-solid-svg-icons';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { PopoverModule } from 'primeng/popover';
import { CheckboxModule } from 'primeng/checkbox';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';

@Component({
    selector: 'jhi-calendar-mobile-overview',
    imports: [
        CalendarMobileMonthPresentationComponent,
        CalendarMobileDayPresentationComponent,
        TranslateDirective,
        FaIconComponent,
        CalendarSubscriptionPopoverComponent,
        PopoverModule,
        CheckboxModule,
        FormsModule,
        ButtonModule,
    ],
    templateUrl: './calendar-mobile-overview.component.html',
    styleUrl: './calendar-mobile-overview.component.scss',
})
export class CalendarMobileOverviewComponent implements OnInit, OnDestroy {
    private calendarService = inject(CalendarService);
    private activatedRoute = inject(ActivatedRoute);
    private activatedRouteSubscription?: Subscription;

    readonly CalendarEventFilterOption = CalendarEventFilterOption;
    readonly faXmark = faXmark;
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;
    readonly faFilter = faFilter;
    readonly faArrowUpFromBracket = faArrowUpFromBracket;

    firstDateOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    selectedDate = signal<Dayjs | undefined>(undefined);
    weekdayNameKeys = utils.getWeekDayNameKeys();
    isLoading = signal<boolean>(false);
    calendarSubscriptionToken = this.calendarService.subscriptionToken;
    currentCourseId = signal<number | undefined>(undefined);
    lectureFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.LectureEvents));
    exerciseFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.ExerciseEvents));
    tutorialFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.TutorialEvents));
    examFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.ExamEvents));

    ngOnInit(): void {
        this.activatedRouteSubscription = this.activatedRoute.parent?.paramMap.subscribe((parameterMap) => {
            const courseIdParameter = parameterMap.get('courseId');
            if (courseIdParameter) {
                this.currentCourseId.set(+courseIdParameter);
                this.loadEventsForCurrentMonth();
            }
        });

        this.calendarService.loadSubscriptionToken().subscribe();
    }

    toggleFilterOption(option: CalendarEventFilterOption) {
        if (this.calendarService.includedEventFilterOptions().includes(option)) {
            this.calendarService.includedEventFilterOptions.update((oldOptions) => oldOptions.filter((otherOption) => otherOption !== option));
        } else {
            this.calendarService.includedEventFilterOptions.update((oldOptions) => [...oldOptions, option]);
        }
    }

    ngOnDestroy() {
        this.activatedRouteSubscription?.unsubscribe();
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
