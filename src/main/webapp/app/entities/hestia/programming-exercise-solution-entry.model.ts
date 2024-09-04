import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseTestCase } from 'app/entities/programming/programming-exercise-test-case.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';

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
