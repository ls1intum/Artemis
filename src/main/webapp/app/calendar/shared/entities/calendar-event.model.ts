import { Dayjs } from 'dayjs/esm';

export class CalendarEvent {
    constructor(
        public id: string,
        public title: string,
        public startDate: Dayjs,
        public endDate?: Dayjs,
        public location?: string,
        public facilitator?: string,
    ) {}

    isTutorialEvent(): boolean {
        return this.id.startsWith('tutorial');
    }

    isLectureEvent(): boolean {
        return this.id.startsWith('lecture');
    }

    isExamEvent(): boolean {
        return this.id.startsWith('exam');
    }

    isQuizExerciseEvent(): boolean {
        return this.id.startsWith('quizExercise');
    }

    isTextExerciseEvent(): boolean {
        return this.id.startsWith('textExercise');
    }

    isModelingExerciseEvent(): boolean {
        return this.id.startsWith('modelingExercise');
    }

    isProgrammingExercise(): boolean {
        return this.id.startsWith('programmingExercise');
    }

    isExerciseEvent(): boolean {
        const id = this.id;
        return (
            id.startsWith('quizExercise') ||
            id.startsWith('fileUploadExercise') ||
            id.startsWith('textExercise') ||
            id.startsWith('modelingExercise') ||
            id.startsWith('programmingExercise')
        );
    }
}

export interface CalendarEventDTO {
    id: string;
    title: string;
    startDate: string;
    endDate?: string;
    location?: string;
    facilitator?: string;
}
