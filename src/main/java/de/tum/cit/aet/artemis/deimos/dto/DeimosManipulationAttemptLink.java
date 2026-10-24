package de.tum.cit.aet.artemis.deimos.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Row for the Deimos completion email: a participation containing an imitated fence marker.
 *
 * @param url                   deep link to the submissions view of the participation
 * @param participationId       the participation id
 * @param findings              the first findings, in order of appearance
 * @param findingCount          the total number of distinct findings, which may exceed {@code findings.size()}
 * @param llmMalicious          the model's classification, or {@code null} when no verdict could be obtained
 * @param rationale             the model's rationale, or {@code null} when no verdict could be obtained
 * @param failureTranslationKey the i18n key of the reason no verdict was obtained, or {@code null} when there is a verdict
 */
public record DeimosManipulationAttemptLink(String url, long participationId, List<DeimosManipulationFinding> findings, long findingCount, @Nullable Boolean llmMalicious,
        @Nullable String rationale, @Nullable String failureTranslationKey) {
}
