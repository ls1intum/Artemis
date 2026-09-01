package de.tum.cit.aet.artemis.deimos.dto;

/**
 * Row for the Deimos completion email: participation id and failure reason for a failed analysis.
 */
public record DeimosFailedAnalysisDetail(long participationId, String reason) {
}
