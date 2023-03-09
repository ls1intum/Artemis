import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { ProgrammingExerciseTestCase, ProgrammingExerciseTestCaseType, Visibility } from 'app/entities/programming-exercise-test-case.model';

export enum TaskAdditionalEnum {
    Mixed = 'MIXED',
}

export class ProgrammingExerciseTask extends ProgrammingExerciseServerSideTask {
    testCases: ProgrammingExerciseTestCase[];
    weight?: number;
    bonusMultiplier?: number;
    bonusPoints?: number;
    visibility?: Visibility | TaskAdditionalEnum;
    type?: ProgrammingExerciseTestCaseType | TaskAdditionalEnum;
}
