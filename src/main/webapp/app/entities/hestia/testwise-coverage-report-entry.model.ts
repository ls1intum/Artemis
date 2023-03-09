import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class TestwiseCoverageReportEntry implements BaseEntity {
    public id?: number;

    public startLine?: number;
    public lineCount?: number;
    public testCase?: ProgrammingExerciseTestCase;

    constructor() {}
}
