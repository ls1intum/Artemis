import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { TestCaseStats } from './programming-exercise-test-case-statistics.model';

export enum Visibility {
    Always = 'ALWAYS',
    AfterDueDate = 'AFTER_DUE_DATE',
    Never = 'NEVER',
}

export enum ProgrammingExerciseTestCaseType {
    STRUCTURAL = 'STRUCTURAL',
    BEHAVIORAL = 'BEHAVIORAL',
    DEFAULT = 'DEFAULT',
}

export class ProgrammingExerciseTestCase implements BaseEntity {
    id?: number;
    testName?: string;
    weight?: number;
    bonusMultiplier?: number;
    bonusPoints?: number;
    active?: boolean;
    visibility?: Visibility;
    exercise?: ProgrammingExercise;
    type?: ProgrammingExerciseTestCaseType;
    solutionEntries?: ProgrammingExerciseSolutionEntry[];

    // Utility information that is not actually part of the object stored on the server
    resultingPoints?: number;
    resultingPointsPercent?: number;
    testCaseStats?: TestCaseStats;
    changed?: boolean;
}
