import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';

export interface ProgrammingExerciseOptions {
    automaticSubmissionRunAfterDueDate: boolean;
    programmingExercise: ProgrammingExercise;
}
