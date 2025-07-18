import { Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgClass } from '@angular/common';
import { finalize } from 'rxjs/operators';
import dayjs, { Dayjs } from 'dayjs/esm';
import 'dayjs/locale/de';
import 'dayjs/locale/en';
import isoWeek from 'dayjs/plugin/isoWeek';
import isSameOrBefore from 'dayjs/plugin/isSameOrBefore';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarMonthPresentationComponent } from 'app/core/calendar/overview/calendar-month-presentation/calendar-month-presentation.component';
import { CalendarWeekPresentationComponent } from 'app/core/calendar/overview/calendar-week-presentation/calendar-week-presentation.component';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEventFilterComponent } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';

dayjs.extend(isoWeek);
dayjs.extend(isSameOrBefore);

@Component({
    selector: 'jhi-calendar-desktop',
    imports: [
        CalendarMonthPresentationComponent,
        CalendarWeekPresentationComponent,
        CalendarEventFilterComponent,
        NgClass,
        FaIconComponent,
        TranslateDirective,
        TranslateDirective,
    ],
    templateUrl: './calendar-overview.component.html',
    styleUrl: './calendar-overview.component.scss',
})
export class CalendarOverviewComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private courseId?: number;
    private currentLocale: WritableSignal<string>;

    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;
    presentation = signal<'week' | 'month'>('month');
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDayOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
    isLoading = signal<boolean>(false);

    constructor(
        private calendarEventService: CalendarEventService,
        private translateService: TranslateService,
    ) {
        this.currentLocale = signal(this.translateService.currentLang);
    }

    ngOnInit(): void {
        this.translateService.onLangChange.subscribe((event) => {
            this.currentLocale.set(event.lang);
        });

        this.activatedRoute.parent?.paramMap.subscribe((parameterMap) => {
            const courseIdParameter = parameterMap.get('courseId');
            if (courseIdParameter) {
                this.courseId = +courseIdParameter;
                this.loadEventsForCurrentMonth();
            }
        });
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
        if (!this.courseId) return;
        this.isLoading.set(true);
        this.calendarEventService
            .loadEventsForCurrentMonth(this.courseId, this.firstDayOfCurrentMonth())
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe();
    }
}
