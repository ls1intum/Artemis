import { Component, computed, signal, viewChild } from '@angular/core';
import { CalendarEvent } from 'app/openapi/model/calendarEvent';
import { Popover, PopoverModule } from 'primeng/popover';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faClock, faLocationDot, faUser, faXmark } from '@fortawesome/free-solid-svg-icons';
import * as utils from 'app/calendar/shared/util/calendar-util';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

interface EventData {
    title: string;
    icon: IconProp;
    eventTypeNameKey: string;
    time: string;
    location?: string;
    facilitator?: string;
}

@Component({
    selector: 'jhi-calendar-event-detail-popover-component',
    imports: [PopoverModule, TranslateDirective, FaIconComponent],
    templateUrl: './calendar-event-detail-popover.component.html',
    styleUrl: './calendar-event-detail-popover.component.scss',
})
export class CalendarEventDetailPopoverComponent {
    private static EVENT_TYPE_NAME_KEY_MAP: Record<CalendarEvent.TypeEnum, string> = {
        [CalendarEvent.TypeEnum.Lecture]: 'artemisApp.calendar.eventTypeName.lecture',
        [CalendarEvent.TypeEnum.Tutorial]: 'artemisApp.calendar.eventTypeName.tutorial',
        [CalendarEvent.TypeEnum.Exam]: 'artemisApp.calendar.eventTypeName.exam',
        [CalendarEvent.TypeEnum.QuizExercise]: 'artemisApp.calendar.eventTypeName.quiz',
        [CalendarEvent.TypeEnum.TextExercise]: 'artemisApp.calendar.eventTypeName.text',
        [CalendarEvent.TypeEnum.ModelingExercise]: 'artemisApp.calendar.eventTypeName.modeling',
        [CalendarEvent.TypeEnum.ProgrammingExercise]: 'artemisApp.calendar.eventTypeName.programming',
        [CalendarEvent.TypeEnum.FileUploadExercise]: 'artemisApp.calendar.eventTypeName.fileUpload',
    };

    readonly faXmark = faXmark;
    readonly faClock = faClock;
    readonly faUser = faUser;
    readonly faLocationDot = faLocationDot;

    event = signal<IdentifiableCalendarEvent | undefined>(undefined);
    eventData = computed(() => this.computeEventData());
    popover = viewChild<Popover>('popover');
    isOpen = signal(false);

    open(mouseEvent: MouseEvent, event: IdentifiableCalendarEvent) {
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

    private computeEventData(): EventData | undefined {
        const event = this.event();
        if (!event) {
            return undefined;
        }
        return {
            title: event.title,
            icon: utils.getIconForEventType(event.type),
            eventTypeNameKey: CalendarEventDetailPopoverComponent.EVENT_TYPE_NAME_KEY_MAP[event.type],
            time: event.endDate ? `${event.startDate.format('HH:mm')}-${event.endDate.format('HH:mm')}` : String(event.startDate.format('HH:mm')),
            location: event.location,
            facilitator: event.facilitator,
        };
    }
}
