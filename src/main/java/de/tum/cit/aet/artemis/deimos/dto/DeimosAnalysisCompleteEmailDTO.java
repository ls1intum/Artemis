package de.tum.cit.aet.artemis.deimos.dto;

import java.util.List;

/**
 * Typed email context for the Deimos analysis-complete notification.
 * Replaces the untyped {@code Map<String, Object>} previously passed to the Thymeleaf template.
 *
 * @param failureBreakdown per-category failure counts, so a run in which the model was unreachable is not mistaken for
 *                             a run in which there was simply nothing to analyse
 */
public record DeimosAnalysisCompleteEmailDTO(long courseId, String courseTitle, String scopeTitle, long analyzed, long manipulationAttemptCount, long maliciousCount,
        long incompletelyCheckedCount, long benignCount, long failed, String notificationUrl, List<DeimosManipulationAttemptLink> manipulationAttemptLinks,
        List<DeimosMaliciousParticipationLink> maliciousParticipationLinks, List<DeimosIncompletelyCheckedLink> incompletelyCheckedLinks,
        List<DeimosFailedAnalysisDetail> failedAnalysisDetails, List<DeimosFailureBreakdownEntry> failureBreakdown) {
}
