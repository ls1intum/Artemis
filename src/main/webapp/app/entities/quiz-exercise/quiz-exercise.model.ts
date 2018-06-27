import { Question } from '../question';
import { QuizPointStatistic } from '../quiz-point-statistic';
import { Exercise } from '../exercise';

export class QuizExercise extends Exercise {
    constructor(
        public id?: number,
        public description?: string,
        public explanation?: string,
        public randomizeQuestionOrder?: boolean,
        public isVisibleBeforeStart?: boolean,
        public isOpenForPractice?: boolean,
        public isPlannedToStart?: boolean,
        public duration?: number,
        public quizPointStatistic?: QuizPointStatistic,
        public questions?: Question[],
        public releaseDate?: any,
        public dueDate?: Date,
        public maxScore?: number,
        public status?: string,
        public isActiveQuiz?: boolean,
        public isPracticeModeAvailable?: boolean
    ) {
        super();
        this.randomizeQuestionOrder = false;
        this.isVisibleBeforeStart = false;
        this.isOpenForPractice = false;
        this.isPlannedToStart = false;
    }
}
