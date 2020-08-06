import { Submission } from 'app/entities/submission.model';
import { Exercise } from 'app/entities/exercise.model';

export abstract class ExamSubmissionComponent {
    abstract hasUnsavedChanges(): boolean;
    abstract updateSubmissionFromView(): void;

    /**
     * is called when the component becomes active / visible
     */
    abstract onActivate(): void;

    abstract getSubmission(): Submission | null;
    abstract getExercise(): Exercise;
}
