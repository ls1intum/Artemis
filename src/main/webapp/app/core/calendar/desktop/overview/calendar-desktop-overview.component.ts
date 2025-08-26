import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NgClass } from '@angular/common';
import { finalize } from 'rxjs/operators';
import dayjs, { Dayjs } from 'dayjs/esm';
import 'dayjs/esm/locale/en';
import 'dayjs/esm/locale/de';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight, faCopy } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarDesktopMonthPresentationComponent } from 'app/core/calendar/desktop/month-presentation/calendar-desktop-month-presentation.component';
import { CalendarDesktopWeekPresentationComponent } from 'app/core/calendar/desktop/week-presentation/calendar-desktop-week-presentation.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEventFilterComponent, CalendarEventFilterComponentVariant } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';
import { Popover } from 'primeng/popover';
import { Checkbox } from 'primeng/checkbox';
import { FormsModule } from '@angular/forms';
import { SelectButton } from 'primeng/selectbutton';
import { AlertService } from 'app/shared/service/alert.service';
import { TextFieldModule } from '@angular/cdk/text-field';

@Component({
    selector: 'jhi-calendar-desktop-overview',
    imports: [
        CalendarDesktopMonthPresentationComponent,
        CalendarDesktopWeekPresentationComponent,
        CalendarEventFilterComponent,
        NgClass,
        FaIconComponent,
        TranslateDirective,
        Popover,
        Checkbox,
        FormsModule,
        SelectButton,
        TextFieldModule,
    ],
    templateUrl: './calendar-desktop-overview.component.html',
    styleUrl: './calendar-desktop-overview.component.scss',
})
export class CalendarDesktopOverviewComponent implements OnInit, OnDestroy {
    private calendarService = inject(CalendarService);
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);
    private activatedRouteSubscription?: Subscription;
    private courseId = signal<number | undefined>(undefined);
    private currentLocaleSubscription?: Subscription;
    private currentLocale = signal(this.translateService.currentLang);
    private calendarSubscriptionToken = signal<string | undefined>(undefined);

    readonly CalendarEventFilterComponentVariant = CalendarEventFilterComponentVariant;
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;
    readonly faCopy = faCopy;
    presentation = signal<'week' | 'month'>('month');
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDayOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
    isLoading = signal<boolean>(false);
    calendarSubscriptionUrl = computed<string | undefined>(() =>
        this.buildCalendarSubscriptionURL(
            this.courseId(),
            this.calendarSubscriptionToken(),
            this.includeLectureEvents(),
            this.includeExerciseEvents(),
            this.includeTutorialEvents(),
            this.includeExamEvents(),
            this.language(),
        ),
    );
    includeLectureEvents = signal(true);
    includeExerciseEvents = signal(true);
    includeTutorialEvents = signal(true);
    includeExamEvents = signal(true);
    language = signal<'ENGLISH' | 'GERMAN'>('ENGLISH');
    languageOptions = [
        { label: 'English', value: 'ENGLISH' },
        { label: 'German', value: 'GERMAN' },
    ];

    ngOnInit(): void {
        this.currentLocaleSubscription = this.translateService.onLangChange.subscribe((event) => {
            this.currentLocale.set(event.lang);
        });

        this.activatedRouteSubscription = this.activatedRoute.parent?.paramMap.subscribe((parameterMap) => {
            const courseIdParameter = parameterMap.get('courseId');
            if (courseIdParameter) {
                this.courseId.set(+courseIdParameter);
                this.loadEventsForCurrentMonth();
            }
        });

        this.calendarService.loadSubscriptionToken().subscribe({
            next: (token) => this.calendarSubscriptionToken.set(token),
            error: () => this.alertService.addErrorAlert(''), // TODO: add error message string
        });
    }

    private buildCalendarSubscriptionURL(
        courseId: number | undefined,
        subscriptionToken: string | undefined,
        includeLectureEvents: boolean,
        includeExerciseEvents: boolean,
        includeTutorialEvents: boolean,
        includeExamEvents: boolean,
        language: 'ENGLISH' | 'GERMAN',
    ): string | undefined {
        if (!courseId || !subscriptionToken) return undefined;
        const origin = window.location.origin;
        const route = `/api/core/calendar/courses/${courseId}/subscription/calendar-events.ics`;

        const filterOptions: string[] = [];
        if (includeLectureEvents) filterOptions.push('LECTURES');
        if (includeExerciseEvents) filterOptions.push('EXERCISES');
        if (includeTutorialEvents) filterOptions.push('TUTORIALS');
        if (includeExamEvents) filterOptions.push('EXAMS');
        const queryParameters = new URLSearchParams();
        queryParameters.set('token', subscriptionToken);
        if (filterOptions.length > 0) {
            queryParameters.set('filterOptions', filterOptions.join(','));
        }
        queryParameters.set('language', language);

        return origin + route + '?' + queryParameters.toString();
    }

    ngOnDestroy() {
        this.currentLocaleSubscription?.unsubscribe();
        this.activatedRouteSubscription?.unsubscribe();
    }

    goToPrevious(): void {
        if (this.presentation() === 'week') {
            this.firstDayOfCurrentWeek.update((current) => current.subtract(1, 'week'));
            const firstDayOfCurrentWeek = this.firstDayOfCurrentWeek();
            const firstDayOfCurrentMonth = this.firstDayOfCurrentMonth();
            if (firstDayOfCurrentWeek.isBefore(firstDayOfCurrentMonth)) {
                this.firstDayOfCurrentMonth.update((current) => current.subtract(1, 'month'));
            }
        } else {
            this.firstDayOfCurrentMonth.update((current) => current.subtract(1, 'month'));
            this.firstDayOfCurrentWeek.set(this.firstDayOfCurrentMonth().startOf('isoWeek'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToNext(): void {
        if (this.presentation() === 'week') {
            this.firstDayOfCurrentWeek.update((current) => current.add(1, 'week'));
            const endOfCurrentWeek = this.firstDayOfCurrentWeek().endOf('isoWeek');
            const endOfCurrentMonth = this.firstDayOfCurrentMonth().endOf('month');
            if (endOfCurrentWeek.isAfter(endOfCurrentMonth)) {
                this.firstDayOfCurrentMonth.update((current) => current.add(1, 'month'));
            }
        } else {
            this.firstDayOfCurrentMonth.update((current) => current.add(1, 'month'));
            this.firstDayOfCurrentWeek.set(this.firstDayOfCurrentMonth().startOf('isoWeek'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToToday(): void {
        this.firstDayOfCurrentMonth.set(dayjs().startOf('month'));
        this.firstDayOfCurrentWeek.set(dayjs().startOf('isoWeek'));
        this.loadEventsForCurrentMonth();
    }

    getMonthDescription(): string {
        const currentLocale = this.currentLocale();
        if (this.presentation() === 'month') {
            return this.firstDayOfCurrentMonth().locale(currentLocale).format('MMMM YYYY');
        } else {
            const firstDayOfCurrentWeek = this.firstDayOfCurrentWeek().locale(currentLocale);
            const lastDayOfCurrentWeek = this.firstDayOfCurrentWeek().endOf('isoWeek').locale(currentLocale);
            if (lastDayOfCurrentWeek.isSame(firstDayOfCurrentWeek, 'month')) {
                return firstDayOfCurrentWeek.format('MMMM YYYY');
            } else {
                return firstDayOfCurrentWeek.format('MMMM') + ' | ' + lastDayOfCurrentWeek.format('MMMM YYYY');
            }
        }
    }

    private loadEventsForCurrentMonth(): void {
        const courseId = this.courseId();
        if (!courseId) return;
        this.isLoading.set(true);
        this.calendarService
            .loadEventsForCurrentMonth(courseId, this.firstDayOfCurrentMonth())
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe();
    }
}
