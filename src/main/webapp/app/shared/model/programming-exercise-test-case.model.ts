import { IExercise } from 'app/shared/model/exercise.model';

export const enum TestCase {
    STRUCTURAL = 'STRUCTURAL',
    BEHAVIOR = 'BEHAVIOR',
}

export interface IProgrammingExerciseTestCase {
    id?: number;
    file_name?: string;
    test_name?: string;
    type?: TestCase;
    weight?: number;
    exercise?: IExercise;
}

export class ProgrammingExerciseTestCase implements IProgrammingExerciseTestCase {
    constructor(public id?: number, public file_name?: string, public test_name?: string, public type?: TestCase, public weight?: number, public exercise?: IExercise) {}
}
