export interface UpdateExternalLLMUsageDto {
    /**
     * CLOUD_AI if the user has accepted the LLM usage policy, else NO_AI. This behavior will be changed in a follow-up PR.
     */
    selection: string;
}
