import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';

export interface PositionInfo {
    top: number;
    height: number;
    left: number;
    width: number;
}

export interface CalendarEventAndMetadata {
    event: CalendarEvent;
    position: PositionInfo;
    eventSubtypeDescriptor?: string;
}
