/**
 * Artemis Application Server API
 * NOTE: This file is auto-generated. Do not edit manually.
 */

import { AssessmentCriteriaModelingContext } from './assessment-criteria-modeling-context';

/** Context used to generate structured assessment criteria. */
export interface AssessmentCriteriaGenerationRequest {
    /** Supported exercise type. */
    exerciseType: AssessmentCriteriaGenerationRequestExerciseTypeEnum;
    /** Current problem statement. */
    problemStatement: string;
    /** Maximum regular score. */
    maxPoints: number;
    /** Maximum bonus score. */
    bonusPoints: number;
    /** General assessment instructions. */
    gradingInstructions?: string;
    /** Required context for modeling exercises. */
    modelingContext?: AssessmentCriteriaModelingContext;
}

export type AssessmentCriteriaGenerationRequestExerciseTypeEnum = 'TEXT' | 'MODELING';

export const AssessmentCriteriaGenerationRequestExerciseTypeEnum = {
    Text: 'TEXT' as const,
    Modeling: 'MODELING' as const,
} as const;
