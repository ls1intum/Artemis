import { Signal } from '@angular/core';

/**
 * Common contract for exercise participation components rendered inside ExerciseSplitPanelComponent.
 * Implementing this interface lets the parent shell trigger submission without a static import
 * of the concrete component class — keeping each exercise type in its own lazy chunk.
 */
export interface ExerciseSubmission {
    submitExercise(): void;

    /**
     * Optional: whether the surface can be submitted right now.
     *
     * The shell offers Submit as soon as a participation exists, which is wrong wherever the surface has gone
     * read-only — an assessed submission, for instance, where pressing Submit would resubmit unchanged work. A
     * component that can become read-only exposes this so the shell stops offering an action that does nothing.
     * A signal rather than a method, because the shell reads it from a computed.
     */
    readonly canSubmitExercise?: Signal<boolean>;
}

/**
 * Runtime duck-type guard for {@link ExerciseSubmission}.
 *
 * The split panel reads its participation component out of a router outlet, which is typed as
 * {@code unknown} — so the contract cannot be verified at compile time. Guarding instead of asserting
 * keeps the submit action a no-op (its behaviour before the interface was introduced) if a future
 * child route renders a component that does not participate in submission, rather than throwing.
 */
export function isExerciseSubmission(component: unknown): component is ExerciseSubmission {
    return typeof component === 'object' && component !== null && 'submitExercise' in component && typeof component.submitExercise === 'function';
}
