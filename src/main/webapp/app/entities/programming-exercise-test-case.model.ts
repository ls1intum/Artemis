import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export interface IProgrammingExerciseTestCase {
    id: number;
    testName: string;
    weight: number;
    active: boolean;
    afterDueDate: boolean;
    exercise: ProgrammingExercise;
}

export class ProgrammingExerciseTestCase implements IProgrammingExerciseTestCase {
    constructor(public id: number, public testName: string, public weight: number, public active: boolean, public afterDueDate: boolean, public exercise: ProgrammingExercise) {
        this.active = this.active || false;
    }
}
