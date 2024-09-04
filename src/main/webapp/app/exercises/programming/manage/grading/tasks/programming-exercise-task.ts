import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { TestCaseStats } from 'app/entities/programming/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase, ProgrammingExerciseTestCaseType, Visibility } from 'app/entities/programming/programming-exercise-test-case.model';

export class ProgrammingExerciseTask extends ProgrammingExerciseServerSideTask {
    declare testCases: ProgrammingExerciseTestCase[];
    weight?: number;
    bonusMultiplier?: number;
    bonusPoints?: number;
    visibility?: Visibility;
    type?: ProgrammingExerciseTestCaseType | 'MIXED';
    resultingPoints?: number;
    resultingPointsPercent?: number;
    stats: TestCaseStats | undefined;
}
