import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { ProgrammingExerciseTestCase, ProgrammingExerciseTestCaseType, Visibility } from 'app/entities/programming-exercise-test-case.model';

export class ProgrammingExerciseTask extends ProgrammingExerciseServerSideTask {
    testCases: ProgrammingExerciseTestCase[];
    weight?: number;
    bonusMultiplier?: number;
    bonusPoints?: number;
    visibility?: Visibility;
    type?: ProgrammingExerciseTestCaseType;
}
