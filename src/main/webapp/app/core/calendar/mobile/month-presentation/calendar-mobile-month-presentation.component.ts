import { Component, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Dayjs } from 'dayjs/esm';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarMonthPresentationComponent } from 'app/core/calendar/shared/calendar-month-presentation/calendar-month-presentation-component.directive';

@Component({
    selector: 'jhi-calendar-mobile-month-presentation',
    imports: [CalendarDayBadgeComponent, NgTemplateOutlet],
    templateUrl: './calendar-mobile-month-presentation.component.html',
    styleUrl: './calendar-mobile-month-presentation.component.scss',
})
export class CalendarMobileMonthPresentationComponent extends CalendarMonthPresentationComponent {
    onDateSelected = output<Dayjs>();
}
