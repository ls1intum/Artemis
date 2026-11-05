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
    if (participationType === ProgrammingExerciseParticipationType.SOLUTION && ((hasScore && result.score !== 100) || result.successful === false)) {
        return 'artemisApp.programmingExercise.status.solutionFailsTest';
    }
    return undefined;
}
