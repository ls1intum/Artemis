import { Submission } from 'app/entities/submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { ChangeDetectorRef } from '@angular/core';

export abstract class ExamSubmissionComponent {
    abstract hasUnsavedChanges(): boolean;
    abstract updateSubmissionFromView(): void;
    abstract updateViewFromSubmission(): void;

    protected constructor(protected changeDetectorReference: ChangeDetectorRef) {}

    /**
     * Should be called when the component becomes active / visible. It reattaches to Angular's change detection.
     * For further customisation, individual submission components can override.
     */
    onActivate(): void {
        this.changeDetectorReference.reattach();
    }

    /**
     * Should be called when the component becomes deactivated / not visible. It detaches from Angular's change detection.
     * For further customisation, individual submission components can override.
     */
    onDeactivate(): void {
        this.changeDetectorReference.detach();
    }

    abstract getSubmission(): Submission | null;
    abstract getExercise(): Exercise;
}
