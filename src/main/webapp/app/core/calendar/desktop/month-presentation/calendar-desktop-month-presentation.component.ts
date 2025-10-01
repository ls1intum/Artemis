import { Component, signal } from '@angular/core';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';
import { CalendarMonthPresentationComponent } from 'app/core/calendar/shared/calendar-month-presentation/calendar-month-presentation-component.directive';

@Component({
    selector: 'jhi-calendar-desktop-month-presentation',
    imports: [NgClass, NgTemplateOutlet, NgbPopover, FaIconComponent, TranslateDirective, CalendarDayBadgeComponent, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-desktop-month-presentation.component.html',
    styleUrls: ['./calendar-desktop-month-presentation.component.scss'],
})
export class CalendarDesktopMonthPresentationComponent extends CalendarMonthPresentationComponent {
    private popover?: NgbPopover;

    readonly weekdayNameKeys = utils.getWeekDayNameKeys();

    selectedEvent = signal<CalendarEvent | undefined>(undefined);

    openPopover(event: CalendarEvent, popover: NgbPopover) {
        if (this.selectedEvent() === event) {
            this.closePopover();
            return;
        }
        this.selectedEvent.set(event);
        this.popover?.close();
        this.popover = popover;
        popover.open();
    }

    closePopover() {
        this.popover?.close();
        this.popover = undefined;
        this.selectedEvent.set(undefined);
    }
}
