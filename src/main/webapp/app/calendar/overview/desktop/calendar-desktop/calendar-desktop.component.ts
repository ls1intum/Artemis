import { Component, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { CalendarDesktopMonthComponent } from 'app/calendar/overview/desktop/calendar-desktop-month/calendar-desktop-month.component';
import { CalendarDesktopWeekComponent } from 'app/calendar/overview/desktop/calendar-desktop-week/calendar-desktop-week.component';

dayjs.extend(isoWeek);

@Component({
    selector: 'jhi-calendar-desktop',
    imports: [CalendarDesktopMonthComponent, CalendarDesktopWeekComponent, NgClass, FaIconComponent],
    templateUrl: './calendar-desktop.component.html',
    styleUrl: './calendar-desktop.component.scss',
})
export class CalendarDesktopComponent {
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;
    presentation = signal<'week' | 'month'>('month');
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDayOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));

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
    }

    goToToday(): void {
        this.firstDayOfCurrentMonth.set(dayjs().startOf('month'));
        this.firstDayOfCurrentWeek.set(dayjs().startOf('isoWeek'));
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
}
