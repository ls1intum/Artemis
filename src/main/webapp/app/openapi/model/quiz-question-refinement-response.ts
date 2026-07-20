/**
 * Discriminated response for a single quiz question refinement, either a success with the refined question or a failure with an error message
 */
export interface QuizQuestionRefinementResponse {
    type: string;
    /** Brief explanation of what was changed during refinement */
    reasoning: string;
    /** Error message describing why the refinement failed */
    error: string;
}

