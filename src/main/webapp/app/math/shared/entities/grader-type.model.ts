/**
 * Mirror of the backend {@code MathType} enum.
 * Discriminator used to dispatch a {@code MathGrader} for a given exercise.
 */
export type GraderType = 'REWRITE_CHAIN' | 'LEAN' | 'ISABELLE' | 'EGG_EGRAPH';

export const DEFAULT_GRADER_TYPE: GraderType = 'REWRITE_CHAIN';

/** Which grader types are wired and usable today. The rest are reserved for milestone 3+. */
export const GRADER_TYPES_AVAILABLE: GraderType[] = ['REWRITE_CHAIN'];

/** Display label for each grader type, used in the editor dropdown. */
export const GRADER_TYPE_LABELS: Record<GraderType, string> = {
    REWRITE_CHAIN: 'Step-by-step (rewrite chain)',
    LEAN: 'Lean (M3, not available)',
    ISABELLE: 'Isabelle/HOL (M3, not available)',
    EGG_EGRAPH: 'egg e-graph (M3, not available)',
};
