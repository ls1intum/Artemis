import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ProgrammingExerciseSolutionEntry implements BaseEntity {
    id?: number;
    filePath?: string;
    previousLine?: number;
    line?: number;
    previousCode?: string;
    code?: string;
    testCase?: ProgrammingExerciseTestCase;
    codeHint?: CodeHint;
}
