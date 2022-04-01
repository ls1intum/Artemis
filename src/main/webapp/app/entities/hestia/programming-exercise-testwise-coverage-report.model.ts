import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExerciseTestwiseCoverageEntry } from 'app/entities/hestia/programming-exercise-testwise-coverage-entry.model';

export class ProgrammingExerciseTestwiseCoverageReport implements BaseEntity {
    public id?: number;

    public testCase?: ProgrammingExerciseTestCase;
    public entries?: ProgrammingExerciseTestwiseCoverageEntry[];

    constructor() {}
}
