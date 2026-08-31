package de.tum.cit.aet.artemis.deimos.dto;

import java.time.ZonedDateTime;
import java.util.List;

public record DeimosBatchSummaryDTO(String runId, String triggerType, String scope, ZonedDateTime from, ZonedDateTime to, long totalCandidates, long analyzed, long maliciousCount,
        long benignCount, long failed, List<ParticipationAnalysis> analyzedParticipations, List<FailedAnalysis> failedAnalyses) {

    public record ParticipationAnalysis(long participationId, long exerciseId, boolean malicious, String rationale) {
    }

    public record FailedAnalysis(long participationId, String reason) {
    }
}
