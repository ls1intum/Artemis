import { Dayjs } from 'dayjs/esm';

export enum CalendarEventType {
    Lecture = 'LECTURE',
    Tutorial = 'TUTORIAL',
    Exam = 'EXAM',
    QuizExercise = 'QUIZ_EXERCISE',
    TextExercise = 'TEXT_EXERCISE',
    ModelingExercise = 'MODELING_EXERCISE',
    ProgrammingExercise = 'PROGRAMMING_EXERCISE',
    FileUploadExercise = 'FILE_UPLOAD_EXERCISE',
}

export enum CalendarEventSubtype {
    StartDate = 'START_DATE',
    EndDate = 'END_DATE',
    StartAndEndDate = 'START_AND_END_DATE',
    ReleaseDate = 'RELEASE_DATE',
    DueDate = 'DUE_DATE',
    PublishResultsDate = 'PUBLISH_RESULTS_DATE',
    StudentReviewStartDate = 'STUDENT_REVIEW_START_DATE',
    StudentReviewEndDate = 'STUDENT_REVIEW_END_DATE',
    AssessmentDueDate = 'ASSESSMENT_DUE_DATE',
}

export class CalendarEvent {
    public id: string;

    constructor(
        public type: CalendarEventType,
        public subtype: CalendarEventSubtype,
        public title: string,
        public startDate: Dayjs,
        public endDate?: Dayjs,
        public location?: string,
        public facilitator?: string,
    ) {
        this.id = window.crypto.randomUUID().toString();
    }

    isOfType(type: CalendarEventType) {
        return this.type === type;
    }

    isOfExerciseType(): boolean {
        return [
            CalendarEventType.ProgrammingExercise,
            CalendarEventType.QuizExercise,
            CalendarEventType.TextExercise,
            CalendarEventType.FileUploadExercise,
            CalendarEventType.ModelingExercise,
        ].includes(this.type);
    }
}

export interface CalendarEventDTO {
    type: string;
    subtype: string;
    title: string;
    startDate: string;
    endDate?: string;
    location?: string;
    facilitator?: string;
}
