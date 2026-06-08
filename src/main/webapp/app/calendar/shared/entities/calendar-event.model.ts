import { Dayjs } from 'dayjs/esm';
import { CalendarEvent } from 'app/openapi/model/calendarEvent';

export class IdentifiableCalendarEvent {
    public id: string;

    constructor(
        public type: CalendarEvent.TypeEnum,
        public title: string,
        public startDate: Dayjs,
        public endDate?: Dayjs,
        public location?: string,
        public facilitator?: string,
    ) {
        this.id = window.crypto.randomUUID().toString();
    }

    isOfType(type: CalendarEvent.TypeEnum): boolean {
        return this.type === type;
    }

    isOfExerciseType(): boolean {
        switch (this.type) {
            case CalendarEvent.TypeEnum.ProgrammingExercise:
            case CalendarEvent.TypeEnum.QuizExercise:
            case CalendarEvent.TypeEnum.TextExercise:
            case CalendarEvent.TypeEnum.FileUploadExercise:
            case CalendarEvent.TypeEnum.ModelingExercise:
                return true;
            default:
                return false;
        }
    }
}
