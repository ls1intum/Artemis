/**
 * Update the LLM selection of the current user.
 */
export enum LLMSelectionDecision {
    CLOUD_AI = 'CLOUD_AI',
    LOCAL_AI = 'LOCAL_AI',
    NO_AI = 'NO_AI',
}

export interface UpdateLLMSelectionDecisionDto {
    /**
     * CLOUD_AI if the user has accepted the cloud LLM usage policy, LOCAL_AI if the user has accepted only the local LLM usage policy, else NO_AI.
     */
    selection: string;
}
