/**
 * Mirror of the backend {@code RuleDirection} enum.
 * Controls whether a rewrite rule may be applied in reverse (template → pattern).
 */
export type RuleDirection = 'FORWARD_ONLY' | 'BIDIRECTIONAL';

/**
 * Mirror of the backend {@code StepDirection} enum.
 * Selected by the student when applying a {@link RuleDirection} rule.
 */
export type StepDirection = 'FORWARD' | 'REVERSE';
