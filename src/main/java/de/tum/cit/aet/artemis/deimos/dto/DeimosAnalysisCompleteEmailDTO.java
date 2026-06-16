package de.tum.cit.aet.artemis.deimos.dto;

import java.util.List;

/**
 * Typed email context for the Deimos analysis-complete notification.
 * Replaces the untyped {@code Map<String, Object>} previously passed to the Thymeleaf template.
 */
public record DeimosAnalysisCompleteEmailDTO(long courseId, String courseTitle, String scopeTitle, long analyzed, long maliciousCount, long benignCount, long failed,
        String notificationUrl, List<DeimosMaliciousParticipationLink> maliciousParticipationLinks) {
}
