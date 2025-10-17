import { Component } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover-component/calendar-event-detail-popover.component';
import { CalendarMonthPresentationComponent } from 'app/core/calendar/shared/calendar-month-presentation/calendar-month-presentation-component.directive';

@Component({
    selector: 'jhi-calendar-desktop-month-presentation',
    imports: [NgTemplateOutlet, FaIconComponent, TranslateDirective, CalendarDayBadgeComponent, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-desktop-month-presentation.component.html',
    styleUrls: ['./calendar-desktop-month-presentation.component.scss'],
})
export class CalendarDesktopMonthPresentationComponent extends CalendarMonthPresentationComponent {
    readonly weekdayNameKeys = utils.getWeekDayNameKeys();
}
