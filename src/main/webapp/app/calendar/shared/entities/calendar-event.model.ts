import { Dayjs } from 'dayjs/esm';
import { CalendarEventType } from 'app/openapi/model/calendar-event-type';
import { generateUuid } from 'app/foundation/util/crypto.utils';

export class IdentifiableCalendarEvent {
    public id: string;

    constructor(
        public type: CalendarEventType,
        public title: string,
        public startDate: Dayjs,
        public endDate?: Dayjs,
        public location?: string,
        public facilitator?: string,
    ) {
        this.id = generateUuid();
    }

    isOfType(type: CalendarEventType): boolean {
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
