import { Dayjs } from 'dayjs/esm';
import { v4 as uuidv4 } from 'uuid';

export enum CalendarEventType {
    Lecture = 'lecture',
    Tutorial = 'tutorial',
    Exam = 'exam',
    QuizExercise = 'quizExercise',
    TextExercise = 'textExercise',
    ModelingExercise = 'modelingExercise',
    ProgrammingExercise = 'programmingExercise',
    FileUploadExercise = 'fileUploadExercise',
}

export enum CalendarEventSubtype {
    StartDate = 'startDate',
    EndDate = 'endDate',
    StartAndEndDate = 'startAndEndDate',
    ReleaseDate = 'releaseDate',
    DueDate = 'dueDate',
    PublishResultsDate = 'publishResultsDate',
    StudentReviewStartDate = 'studentReviewStartDate',
    StudentReviewEndDate = 'studentReviewEndDate',
    AssessmentDueDate = 'assessmentDueDate',
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
        this.id = uuidv4();
    }

    isTutorialEvent(): boolean {
        return this.type === 'tutorial';
    }

    isLectureEvent(): boolean {
        return this.type === 'lecture';
    }

    isExamEvent(): boolean {
        return this.type === 'exam';
    }

    isQuizExerciseEvent(): boolean {
        return this.type === 'quizExercise';
    }

    isTextExerciseEvent(): boolean {
        return this.type === 'textExercise';
    }

    isModelingExerciseEvent(): boolean {
        return this.type === 'modelingExercise';
    }

    isProgrammingExercise(): boolean {
        return this.type === 'programmingExercise';
    }

    isExerciseEvent(): boolean {
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
