import { Dayjs } from 'dayjs/esm';
import { CalendarEventTypeEnum } from 'app/openapi/models/calendar-event';

export class IdentifiableCalendarEvent {
    public id: string;

    constructor(
        public type: CalendarEventTypeEnum,
        public title: string,
        public startDate: Dayjs,
        public endDate?: Dayjs,
        public location?: string,
        public facilitator?: string,
    ) {
        this.id = window.crypto.randomUUID().toString();
    }

    isOfType(type: CalendarEventTypeEnum): boolean {
        return this.type === type;
    }

    isOfExerciseType(): boolean {
        switch (this.type) {
            case 'PROGRAMMING_EXERCISE':
            case 'QUIZ_EXERCISE':
            case 'TEXT_EXERCISE':
            case 'FILE_UPLOAD_EXERCISE':
            case 'MODELING_EXERCISE':
                return true;
            default:
                return false;
        }
    }
}
