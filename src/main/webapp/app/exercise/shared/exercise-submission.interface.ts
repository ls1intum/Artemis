import { Signal } from '@angular/core';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

/** The purpose of an editor read, chosen by its route or container. */
export type EditorViewingPurpose = 'owner' | 'staff-preview';

/**
 * Common contract for exercise participation components rendered inside ExerciseSplitPanelComponent.
 * Implementing this interface lets the parent shell trigger submission without a static import
 * of the concrete component class — keeping each exercise type in its own lazy chunk.
 */
export interface ExerciseSubmission {
    /** Implementations handle submission errors locally and may finish asynchronously. */
    submitExercise(): void | Promise<void>;

    /**
     * Whether the surface can be submitted right now. Implemented only by components that can go read-only — an
     * assessed submission, for instance — so the shell stops offering a Submit that would resubmit unchanged work.
     * A signal rather than a method, because the shell reads it from a computed.
     */
    readonly canSubmitExercise?: Signal<boolean>;

    /**
     * The participation narrowed to an earlier submission and result that the surface shows, so the shell's problem statement shows
     * the tasks of that result rather than of the latest one. Undefined while the latest submission is shown.
     */
    readonly earlierSubmissionParticipation?: Signal<StudentParticipation | undefined>;
}

/**
 * Runtime duck-type guard for {@link ExerciseSubmission}.
 *
 * The split panel reads its participation component out of a router outlet, which is typed as
 * {@code unknown} — so the contract cannot be verified at compile time. Guarding rather than asserting keeps the
 * submit action a no-op if a child route renders a component that does not participate in submission.
 */
export function isExerciseSubmission(component: unknown): component is ExerciseSubmission {
    return typeof component === 'object' && component !== null && 'submitExercise' in component && typeof component.submitExercise === 'function';
}
