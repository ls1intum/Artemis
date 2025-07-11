import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faClock, faLocationDot, faUser, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';

@Component({
    selector: 'jhi-calendar-event-detail-popover',
    imports: [FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
    templateUrl: './calendar-event-detail-popover.component.html',
    styleUrl: './calendar-event-detail-popover.component.scss',
})
export class CalendarEventDetailPopoverComponent {
    event = input.required<CalendarEvent>();
    onClosePopover = output();

    readonly utils = utils;
    readonly faXmark = faXmark;
    readonly faClock = faClock;
    readonly faUser = faUser;
    readonly faLocationDot = faLocationDot;

    closePopover() {
        this.onClosePopover.emit();
    }
}
