import { Component, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { CalendarDesktopMonthComponent } from 'app/calendar/desktop/calendar-desktop-month/calendar-desktop-month.component';

@Component({
    selector: 'jhi-calendar-desktop',
    imports: [CalendarDesktopMonthComponent, NgClass, FaIconComponent],
    templateUrl: './calendar-desktop.component.html',
    styleUrl: './calendar-desktop.component.scss',
})
export class CalendarDesktopComponent {
    readonly faChevronRight = faChevronRight;
    readonly faChevronLeft = faChevronLeft;
    viewMode = signal<'week' | 'month'>('month');
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));

    goToPreviousMonth(): void {
        this.firstDayOfCurrentMonth.update((current) => current.subtract(1, 'month'));
    }

    goToNextMonth(): void {
        this.firstDayOfCurrentMonth.update((current) => current.add(1, 'month'));
    }

    goToMonthOfToday(): void {
        this.firstDayOfCurrentMonth.set(dayjs().startOf('month'));
    }
}
