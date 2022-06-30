import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';

export enum CodeHintGenerationStep {
    GIT_DIFF,
    COVERAGE,
    SOLUTION_ENTRIES,
    CODE_HINTS,
}
export class CodeHint extends ExerciseHint {
    public solutionEntries?: ProgrammingExerciseSolutionEntry[];
}
