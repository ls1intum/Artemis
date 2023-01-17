import { ExamExercise } from 'app/entities/exam-exercise.model';

export class ExamPage {
    public isOverviewPage?: boolean;
    public exercise?: ExamExercise;

    constructor() {
        this.isOverviewPage = false;
        this.exercise = undefined;
    }
}
