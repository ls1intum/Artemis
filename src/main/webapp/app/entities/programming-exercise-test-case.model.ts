import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export enum TestCaseVisibility {
    Always = 'ALWAYS',
    AfterDueDate = 'AFTER_DUE_DATE',
    Never = 'NEVER',
}

export class ProgrammingExerciseTestCase implements BaseEntity {
    id?: number;
    testName?: string;
    weight?: number;
    bonusMultiplier?: number;
    bonusPoints?: number;
    active?: boolean;
    visibility?: TestCaseVisibility;
    exercise?: ProgrammingExercise;
}
