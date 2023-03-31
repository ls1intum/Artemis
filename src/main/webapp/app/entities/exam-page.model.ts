import { Exercise } from 'app/entities/exercise.model';

export class ExamPage {
    public isOverviewPage?: boolean;
    public isQuizExamPage?: boolean;
    public exercise?: Exercise;

    constructor() {
        this.isOverviewPage = false;
        this.isQuizExamPage = false;
        this.exercise = undefined;
    }
}
