package de.tum.cit.aet.artemis.deimos.dto;

/**
 * One row of the per-category failure breakdown in the Deimos completion email.
 *
 * @param failureType    the failure category
 * @param translationKey the i18n key describing the category to the recipient
 * @param count          how many participations failed with this category
 */
public record DeimosFailureBreakdownEntry(DeimosFailureType failureType, String translationKey, long count) {

    /**
     * Builds an entry for the given failure type and count.
     *
     * @param failureType the failure category
     * @param count       how many participations failed with this category
     * @return the breakdown entry, carrying the i18n key for the category
     */
    public static DeimosFailureBreakdownEntry of(DeimosFailureType failureType, long count) {
        return new DeimosFailureBreakdownEntry(failureType, "email.deimos.analysisComplete.failureType." + failureType.name(), count);
    }
}
