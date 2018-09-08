import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';

export const enum ScoringType {
    ALL_OR_NOTHING = 'ALL_OR_NOTHING',
    PROPORTIONAL_CORRECT_OPTIONS = 'PROPORTIONAL_CORRECT_OPTIONS',
    TRUE_FALSE_NEUTRAL = 'TRUE_FALSE_NEUTRAL'
}

export interface IQuestion {
    id?: number;
    title?: string;
    text?: string;
    hint?: string;
    explanation?: string;
    score?: number;
    scoringType?: ScoringType;
    randomizeOrder?: boolean;
    exercise?: IQuizExercise;
}

export class Question implements IQuestion {
    constructor(
        public id?: number,
        public title?: string,
        public text?: string,
        public hint?: string,
        public explanation?: string,
        public score?: number,
        public scoringType?: ScoringType,
        public randomizeOrder?: boolean,
        public exercise?: IQuizExercise
    ) {
        this.randomizeOrder = this.randomizeOrder || false;
    }
}
