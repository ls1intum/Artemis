package de.tum.cit.aet.artemis.deimos.dto;

/**
 * Row for the Deimos completion email: participation id, failure category and failure reason for a failed analysis.
 *
 * @param participationId the participation id, or {@link DeimosBatchSummaryDTO#BATCH_LEVEL_FAILURE_PARTICIPATION_ID} for a batch-level failure
 * @param failureType     the failure category
 * @param translationKey  the i18n key describing the category to the recipient
 * @param reason          the technical reason
 */
public record DeimosFailedAnalysisDetail(long participationId, DeimosFailureType failureType, String translationKey, String reason) {

    /**
     * Builds a detail row, carrying the i18n key for its category.
     *
     * @param participationId the participation id
     * @param failureType     the failure category, {@link DeimosFailureType#OTHER} when unknown
     * @param reason          the technical reason
     * @return the detail row
     */
    public static DeimosFailedAnalysisDetail of(long participationId, DeimosFailureType failureType, String reason) {
        DeimosFailureType type = failureType != null ? failureType : DeimosFailureType.OTHER;
        return new DeimosFailedAnalysisDetail(participationId, type, DeimosFailureBreakdownEntry.translationKey(type), reason);
    }
}
