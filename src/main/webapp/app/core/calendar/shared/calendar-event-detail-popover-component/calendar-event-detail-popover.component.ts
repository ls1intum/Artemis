import { Component, signal, viewChild } from '@angular/core';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { Popover, PopoverModule } from 'primeng/popover';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faClock, faLocationDot, faUser, faXmark } from '@fortawesome/free-solid-svg-icons';
import * as utils from 'app/core/calendar/shared/util/calendar-util';

@Component({
    selector: 'jhi-calendar-event-detail-popover-component',
    imports: [PopoverModule, TranslateDirective, FaIconComponent],
    templateUrl: './calendar-event-detail-popover.component.html',
    styleUrl: './calendar-event-detail-popover.component.scss',
})
export class CalendarEventDetailPopoverComponent {
    readonly utils = utils;
    readonly faXmark = faXmark;
    readonly faClock = faClock;
    readonly faUser = faUser;
    readonly faLocationDot = faLocationDot;

    event = signal<CalendarEvent | undefined>(undefined);
    popover = viewChild<Popover>('popover');
    isOpen = signal(false);

    open(mouseEvent: MouseEvent, event: CalendarEvent) {
        const popover = this.popover();
        if (popover && !this.isOpen()) {
            this.event.set(event);
            popover.show(mouseEvent, mouseEvent.currentTarget);
        }
    }

    close() {
        this.popover()?.hide();
    }

    onHide() {
        this.isOpen.set(false);
        this.event.set(undefined);
    }

    onShow() {
        this.isOpen.set(true);
    }
}
