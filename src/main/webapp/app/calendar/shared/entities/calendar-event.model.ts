import { Dayjs } from 'dayjs/esm';

export interface CalendarEvent {
    id: string;
    title: string;
    courseName: string;
    startDate: Dayjs;
    endDate?: Dayjs;
    location?: string;
    facilitator?: string;
}

export interface CalendarEventDTO {
    id: string;
    title: string;
    courseName: string;
    startDate: string;
    endDate?: string;
    location?: string;
    facilitator?: string;
}
