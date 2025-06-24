import { Dayjs } from 'dayjs/esm';

export interface CalendarEvent {
    id: string;
    title: string;
    startDate: Dayjs;
    endDate?: Dayjs;
}
