/**
 * Artemis Application Server API
 * NOTE: This file is auto-generated. Do not edit manually.
 */

/** Context used to generate structured assessment criteria. */
export interface AssessmentCriteriaGenerationRequest {
    /** Current problem statement. */
    problemStatement: string;
    /** Maximum regular score. */
    maxPoints: number;
    /** Maximum bonus score. */
    bonusPoints: number;
    /** General assessment instructions. */
    gradingInstructions?: string;
    /** Current example solution, if available. */
    exampleSolution?: string;
    /** Optional exercise-specific context appended to the default prompt. */
    additionalContext?: string;
}
