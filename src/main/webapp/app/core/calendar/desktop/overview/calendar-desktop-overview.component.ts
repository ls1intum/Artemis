import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NgClass } from '@angular/common';
import { finalize } from 'rxjs/operators';
import dayjs, { Dayjs } from 'dayjs/esm';
import 'dayjs/esm/locale/en';
import 'dayjs/esm/locale/de';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarDesktopMonthPresentationComponent } from 'app/core/calendar/desktop/month-presentation/calendar-desktop-month-presentation.component';
import { CalendarDesktopWeekPresentationComponent } from 'app/core/calendar/desktop/week-presentation/calendar-desktop-week-presentation.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEventFilterComponent, CalendarEventFilterComponentVariant } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';
import { AlertService } from 'app/shared/service/alert.service';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';

@Component({
    selector: 'jhi-calendar-desktop-overview',
    imports: [
        CalendarDesktopMonthPresentationComponent,
        CalendarDesktopWeekPresentationComponent,
        CalendarEventFilterComponent,
        NgClass,
        FaIconComponent,
        TranslateDirective,
        CalendarSubscriptionPopoverComponent,
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
    private currentLocaleSubscription?: Subscription;
    private currentLocale = signal(this.translateService.currentLang);

    readonly CalendarEventFilterComponentVariant = CalendarEventFilterComponentVariant;
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;

    presentation = signal<'week' | 'month'>('month');
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDayOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
    isLoading = signal<boolean>(false);
    calendarSubscriptionToken = signal<string | undefined>(undefined);
    currentCourseId = signal<number | undefined>(undefined);

    ngOnInit(): void {
        this.currentLocaleSubscription = this.translateService.onLangChange.subscribe((event) => {
            this.currentLocale.set(event.lang);
        });

        this.activatedRouteSubscription = this.activatedRoute.parent?.paramMap.subscribe((parameterMap) => {
            const courseIdParameter = parameterMap.get('courseId');
            if (courseIdParameter) {
                this.currentCourseId.set(+courseIdParameter);
                this.loadEventsForCurrentMonth();
            }
        });

        this.calendarService.loadSubscriptionToken().subscribe({
            next: (token) => this.calendarSubscriptionToken.set(token),
            error: () => this.alertService.addErrorAlert(''), // TODO: add error message string
        });
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
        const courseId = this.currentCourseId();
        if (!courseId) return;
        this.isLoading.set(true);
        this.calendarService
            .loadEventsForCurrentMonth(courseId, this.firstDayOfCurrentMonth())
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe();
    }
}
