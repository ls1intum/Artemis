import { Submission } from 'app/entities/submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { ChangeDetectorRef } from '@angular/core';

export abstract class ExamSubmissionComponent {
    /**
     * checks whether the component has unsaved changes.
     * It is called in the periodic update timer to determine, if the component needs an update
     */
    abstract hasUnsavedChanges(): boolean;

    /**
     * updates the submission with the values from the displayed content.
     * This is called when the submission is save, so that the latest state is synchronized with the server
     */
    abstract updateSubmissionFromView(): void;

    /**
     * takes the current values from the submission and displays them
     * This is called when a component is initialized, because we want to display the state of the submission.
     * In case the submission has not been edited it is an empty submission.
     */
    abstract updateViewFromSubmission(): void;

    protected constructor(protected changeDetectorReference: ChangeDetectorRef) {}

    /**
     * Should be called when the component becomes active / visible. It activates Angular's change detection for this component.
     * We disabled Angular's change detection for invisible components, because of performance reasons. Here it is activated again,
     * that means once the component becomes active / visible, Angular will check for changes in the application state and update
     * the view if necessary. The performance improvement comes from not checking the components for updates while being invisible
     * For further customisation, individual submission components can override.
     */
    onActivate(): void {
        this.changeDetectorReference.reattach();
    }

    /**
     * Should be called when the component becomes deactivated / not visible. It deactivates Angular's change detection.
     * Angular change detection is responsible for synchronizing the view with the application state (often done over bindings)
     * We disabled Angular's change detection for invisible components for performance reasons. That means, that the component
     * will not be checked for updates and also will not be updated when it is invisible. Note: This works recursively, that means
     * subcomponents will also not be able to check for updates and update the view according to the application state.
     * For further customisation, individual submission components can override.
     */
    onDeactivate(): void {
        this.changeDetectorReference.detach();
    }

    abstract getSubmission(): Submission | null;
    abstract getExercise(): Exercise;
}
