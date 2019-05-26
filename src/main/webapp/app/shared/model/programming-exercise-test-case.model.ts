import { IExercise } from 'app/shared/model/exercise.model';

export interface IProgrammingExerciseTestCase {
    id?: number;
    testName?: string;
    weight?: number;
    active?: boolean;
    exercise?: IExercise;
}

export class ProgrammingExerciseTestCase implements IProgrammingExerciseTestCase {
    constructor(public id?: number, public testName?: string, public weight?: number, public active?: boolean, public exercise?: IExercise) {
        this.active = this.active || false;
    }
}
