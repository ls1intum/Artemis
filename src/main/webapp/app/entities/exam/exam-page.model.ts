import { Exercise } from 'app/exercise/entities/exercise.model';

export class ExamPage {
    public isOverviewPage?: boolean;
    public exercise?: Exercise;

    constructor() {
        this.isOverviewPage = false;
        this.exercise = undefined;
    }
}
