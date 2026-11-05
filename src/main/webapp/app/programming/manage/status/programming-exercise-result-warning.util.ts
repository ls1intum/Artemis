import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';

/** Shared warning policy for the instructor's build results and the course exercise list. */
export function programmingExerciseResultWarning(result: Result | undefined, participationType: ProgrammingExerciseParticipationType | undefined): string | undefined {
    if (!result) {
        return undefined;
    }
    const hasScore = result.score !== undefined && Number.isFinite(result.score);
    if (participationType === ProgrammingExerciseParticipationType.TEMPLATE && hasScore && result.score !== 0) {
        return 'artemisApp.programmingExercise.status.templateFulfillsTest';
    }
    // Bonus points can lift a fully passing solution above 100%. The successful flag is
    // score-derived, so use the test counts to catch failures hidden by bonus points.
    const hasFailedTests = result.testCaseCount !== undefined && result.passedTestCaseCount !== undefined && result.passedTestCaseCount < result.testCaseCount;
    if (participationType === ProgrammingExerciseParticipationType.SOLUTION && ((hasScore && result.score! < 100) || hasFailedTests)) {
        return 'artemisApp.programmingExercise.status.solutionFailsTest';
    }
    return undefined;
}
