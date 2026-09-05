package de.tum.cit.aet.artemis.deimos.dto;

/**
 * Row for the Deimos completion email: deep link to a programming participation (submissions view) and LLM rationale.
 */
public record DeimosMaliciousParticipationLink(String url, long participationId, String rationale) {
}
