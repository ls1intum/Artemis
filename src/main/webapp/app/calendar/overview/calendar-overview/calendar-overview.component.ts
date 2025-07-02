import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import isSameOrBefore from 'dayjs/plugin/isSameOrBefore';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { CalendarMonthPresentationComponent } from 'app/calendar/overview/calendar-month-presentation/calendar-month-presentation.component';
import { CalendarWeekPresentationComponent } from 'app/calendar/overview/calendar-week-presentation/calendar-week-presentation.component';
import { CalendarLegendComponent } from 'app/calendar/shared/calendar-legend/calendar-legend.component';
import { CalendarEventService } from 'app/calendar/shared/service/calendar-event.service';

dayjs.extend(isoWeek);
dayjs.extend(isSameOrBefore);

@Component({
    selector: 'jhi-calendar-desktop',
    imports: [CalendarMonthPresentationComponent, CalendarWeekPresentationComponent, CalendarLegendComponent, NgClass, FaIconComponent],
    templateUrl: './calendar-overview.component.html',
    styleUrl: './calendar-overview.component.scss',
})
export class CalendarOverviewComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private courseId?: number;

    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;
    presentation = signal<'week' | 'month'>('month');
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDayOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));

    constructor(private calendarEventService: CalendarEventService) {}

    ngOnInit(): void {
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
        if (this.presentation() === 'month') {
            return this.firstDayOfCurrentMonth().format('MMMM YYYY');
        } else {
            const firstDayOfCurrentWeek = this.firstDayOfCurrentWeek();
            const lastDayOfCurrentWeek = this.firstDayOfCurrentWeek().endOf('isoWeek');
            if (lastDayOfCurrentWeek.isSame(firstDayOfCurrentWeek, 'month')) {
                return firstDayOfCurrentWeek.format('MMMM YYYY');
            } else {
                return firstDayOfCurrentWeek.format('MMMM') + ' | ' + lastDayOfCurrentWeek.format('MMMM YYYY');
            }
        }
    }

    private loadEventsForCurrentMonth(): void {
        if (!this.courseId) return;
        this.calendarEventService.loadEventsForCurrentMonth(this.courseId, this.firstDayOfCurrentMonth()).subscribe();
    }
}
