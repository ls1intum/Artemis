import { IExerciseResult } from 'app/shared/model//exercise-result.model';

export const enum FeedbackType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL'
}

export interface IFeedback {
    id?: number;
    text?: string;
    detailText?: string;
    positive?: boolean;
    type?: FeedbackType;
    result?: IExerciseResult;
}

export class Feedback implements IFeedback {
    constructor(
        public id?: number,
        public text?: string,
        public detailText?: string,
        public positive?: boolean,
        public type?: FeedbackType,
        public result?: IExerciseResult
    ) {
        this.positive = this.positive || false;
    }
}
