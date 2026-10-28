import { Component } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import * as utils from 'app/calendar/shared/util/calendar-util';
import { CalendarDayBadgeComponent } from 'app/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/calendar/shared/calendar-event-detail-popover-component/calendar-event-detail-popover.component';
import { CalendarMonthPresentationComponent } from 'app/calendar/shared/calendar-month-presentation/calendar-month-presentation-component.directive';

@Component({
    selector: 'jhi-calendar-desktop-month-presentation',
    imports: [
        NgTemplateOutlet,
        FaIconComponent,
        TranslateDirective,
        CalendarDayBadgeComponent,
        CalendarEventDetailPopoverComponent,
        TumAetUiButtonDirective,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
    templateUrl: './calendar-desktop-month-presentation.component.html',
    styleUrls: ['./calendar-desktop-month-presentation.component.scss'],
})
export class CalendarDesktopMonthPresentationComponent extends CalendarMonthPresentationComponent {
    readonly weekdayNameKeys = utils.getWeekdayNameKeys();
    protected readonly faXmark = faXmark;
}
