import { BaseEntity } from './../../shared';

export class QuizExercise implements BaseEntity {
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
        public questions?: BaseEntity[],
    ) {
        this.randomizeQuestionOrder = false;
        this.isVisibleBeforeStart = false;
        this.isOpenForPractice = false;
        this.isPlannedToStart = false;
    }
}
