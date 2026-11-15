/**
 * Update the LLM selection of the current user.
 * Values must match the server-side {@code AiSelectionDecision} enum.
 */
export enum LLMSelectionDecision {
    CLOUD_AI = 'CLOUD_AI',
    LOCAL_AI = 'LOCAL_AI',
    NO_AI = 'NO_AI',
}

/** Client-only sentinel returned when the user dismisses the LLM selection modal without making a choice. Must never be sent to the server. */
export const LLM_MODAL_DISMISSED = 'DISMISSED' as const;
export type LLMModalResult = LLMSelectionDecision | typeof LLM_MODAL_DISMISSED;
