import dayjs from 'dayjs/esm';

export interface CalendarEvent {
    id: string;
    name: string;
    start: dayjs.Dayjs;
    end: dayjs.Dayjs;
}
