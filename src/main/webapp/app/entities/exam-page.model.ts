import { Exercise } from 'app/entities/exercise.model';

export class ExamPage {
    public isOverviewPage?: boolean;
    public exercise?: Exercise;

    constructor() {
        this.isOverviewPage = false;
        this.exercise = undefined;
    }
}
