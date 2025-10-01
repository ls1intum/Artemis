import { Component, OnInit, Signal, computed, effect, inject, signal } from '@angular/core';
import { distinctUntilChanged, map } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
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
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';

type Presentation = 'week' | 'month';

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
export class CalendarDesktopOverviewComponent implements OnInit {
    private calendarService = inject(CalendarService);
    private translateService = inject(TranslateService);
    private activatedRoute = inject(ActivatedRoute);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    readonly CalendarEventFilterComponentVariant = CalendarEventFilterComponentVariant;
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;

    presentation = signal<Presentation>('month');
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDayOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
    isLoading = signal<boolean>(false);
    calendarSubscriptionToken = this.calendarService.subscriptionToken;
    currentCourseId: Signal<number | undefined> = this.getCurrentCourseIdSignal();
    monthDescription = computed<string>(() => this.computeMonthDescription(this.currentLocale(), this.presentation(), this.firstDayOfCurrentMonth(), this.firstDayOfCurrentWeek()));

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

    private computeMonthDescription(currentLocale: string, presentation: Presentation, firstDayOfCurrentMonth: Dayjs, firstDayOfCurrentWeek: Dayjs): string {
        if (presentation === 'month') {
            return firstDayOfCurrentMonth.locale(currentLocale).format('MMMM YYYY');
        } else {
            const localizedFirstDayOfCurrentWeek = firstDayOfCurrentWeek.locale(currentLocale);
            const localizedLastDayOfCurrentWeek = firstDayOfCurrentWeek.endOf('isoWeek').locale(currentLocale);
            if (localizedLastDayOfCurrentWeek.isSame(firstDayOfCurrentWeek, 'month')) {
                return localizedFirstDayOfCurrentWeek.format('MMMM YYYY');
            } else {
                return localizedFirstDayOfCurrentWeek.format('MMMM') + ' | ' + localizedLastDayOfCurrentWeek.format('MMMM YYYY');
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
