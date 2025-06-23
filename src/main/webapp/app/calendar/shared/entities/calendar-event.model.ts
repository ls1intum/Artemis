import { Dayjs } from 'dayjs/esm';

export interface CalendarEvent {
    id: string;
    name: string;
    start: Dayjs;
    end: Dayjs;
}
