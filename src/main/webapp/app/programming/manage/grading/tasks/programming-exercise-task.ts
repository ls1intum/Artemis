import { ProgrammingExerciseServerSideTask } from 'app/entities/programming-exercise-task.model';
import { TestCaseStats } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase, ProgrammingExerciseTestCaseType, Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';

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
