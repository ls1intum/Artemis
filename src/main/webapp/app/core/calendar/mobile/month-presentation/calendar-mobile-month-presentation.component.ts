import { Component, Signal, inject, input, output } from '@angular/core';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { Dayjs } from 'dayjs/esm';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarMonthPresentationService, CalendarMonthPresentationWeek } from 'app/core/calendar/shared/service/calendar-month-presentation.service';

@Component({
    selector: 'jhi-calendar-mobile-month-presentation',
    imports: [NgClass, CalendarDayBadgeComponent, NgTemplateOutlet],
    templateUrl: './calendar-mobile-month-presentation.component.html',
    styleUrl: './calendar-mobile-month-presentation.component.scss',
})
export class CalendarMobileMonthPresentationComponent {
    private monthPresentationService = inject(CalendarMonthPresentationService);

    firstDateOfMonth = input.required<Dayjs>();
    selectDate = output<Dayjs>();

    weeks: Signal<CalendarMonthPresentationWeek[]> = this.monthPresentationService.getWeeks(this.firstDateOfMonth);
}
