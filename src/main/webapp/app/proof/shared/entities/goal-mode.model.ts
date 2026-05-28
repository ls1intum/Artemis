/**
 * Mirror of the backend {@code GoalMode} enum.
 * Controls how the proof goal is encoded: source-to-target derivation or single goal tree closed by tautology.
 */
export type GoalMode = 'TRANSFORMATION' | 'EQUATION';

export const DEFAULT_GOAL_MODE: GoalMode = 'TRANSFORMATION';

/** Display labels for the editor dropdown. */
export const GOAL_MODE_LABELS: Record<GoalMode, string> = {
    TRANSFORMATION: 'Transformation (source → target)',
    EQUATION: 'Equation (single goal, closed by tautology)',
};
