/**
 * Common contract for exercise participation components rendered inside ExerciseSplitPanelComponent.
 * Implementing this interface lets the parent shell trigger submission without a static import
 * of the concrete component class — keeping each exercise type in its own lazy chunk.
 */
export interface ExerciseSubmission {
    submitExercise(): void;
}
