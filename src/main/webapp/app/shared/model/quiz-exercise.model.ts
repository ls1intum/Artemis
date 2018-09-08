import { IQuestion } from 'app/shared/model/question.model';

export interface IQuizExercise {
    id?: number;
    description?: string;
    explanation?: string;
    randomizeQuestionOrder?: boolean;
    allowedNumberOfAttempts?: number;
    isVisibleBeforeStart?: boolean;
    isOpenForPractice?: boolean;
    isPlannedToStart?: boolean;
    duration?: number;
    questions?: IQuestion[];
}

export class QuizExercise implements IQuizExercise {
    constructor(
        public id?: number,
        public description?: string,
        public explanation?: string,
        public randomizeQuestionOrder?: boolean,
        public allowedNumberOfAttempts?: number,
        public isVisibleBeforeStart?: boolean,
        public isOpenForPractice?: boolean,
        public isPlannedToStart?: boolean,
        public duration?: number,
        public questions?: IQuestion[]
    ) {
        this.randomizeQuestionOrder = this.randomizeQuestionOrder || false;
        this.isVisibleBeforeStart = this.isVisibleBeforeStart || false;
        this.isOpenForPractice = this.isOpenForPractice || false;
        this.isPlannedToStart = this.isPlannedToStart || false;
    }
}
