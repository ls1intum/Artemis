import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

/**
 * Result of importing an exam together with its exercises. Failed exercises are reported in two categories so the user
 * gets precise feedback: {@link skippedExercises} (cleanly not imported, nothing persisted) and {@link incompleteExercises}
 * (failed partway and may have left an incomplete exercise that should be reviewed/removed). The arrays are omitted by the
 * server when empty (NON_EMPTY serialization), hence optional.
 */
export interface ExamImportResultDTO {
    exam: Exam;
    skippedExercises?: string[];
    incompleteExercises?: string[];
}

/**
 * Result of importing exercise groups into an existing exam. See {@link ExamImportResultDTO} for the meaning of the two
 * failure categories.
 */
export interface ExerciseGroupImportResultDTO {
    exerciseGroups: ExerciseGroup[];
    skippedExercises?: string[];
    incompleteExercises?: string[];
}
