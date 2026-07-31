package de.tum.cit.aet.artemis.deimos.dto;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DeimosBatchSummaryDTO(String runId, String triggerType, String scope, ZonedDateTime from, ZonedDateTime to, long totalCandidates, long analyzed, long maliciousCount,
        long benignCount, long failed, List<ParticipationAnalysis> analyzedParticipations, List<FailedAnalysis> failedAnalyses) {

    public record ParticipationAnalysis(long participationId, long exerciseId, boolean malicious, String rationale) {
    }

    public record FailedAnalysis(long participationId, DeimosFailureType failureType, String reason) {
    }

    /**
     * Counts the failures per {@link DeimosFailureType}.
     * <p>
     * A single {@code failed} number cannot distinguish "these participations had nothing to analyse" from "the model
     * was unreachable for these participations", which makes a broken run look like a clean one. The per-type counts
     * are surfaced in the completion email so an instructor can tell the two apart.
     *
     * @return the number of failures per type, omitting types that did not occur
     */
    public Map<DeimosFailureType, Long> failureCountsByType() {
        Map<DeimosFailureType, Long> counts = new EnumMap<>(DeimosFailureType.class);
        for (FailedAnalysis failedAnalysis : failedAnalyses) {
            DeimosFailureType type = failedAnalysis.failureType() != null ? failedAnalysis.failureType() : DeimosFailureType.OTHER;
            counts.merge(type, 1L, Long::sum);
        }

        // A run that aborts before reaching individual participations reports every candidate as failed but records a
        // single batch-level detail. Without this reconciliation the email would show "Failed: 200" next to a breakdown
        // adding up to 1, which reads as though 199 participations were fine.
        long accountedFor = counts.values().stream().mapToLong(Long::longValue).sum();
        if (failed > accountedFor) {
            counts.merge(DeimosFailureType.OTHER, failed - accountedFor, Long::sum);
        }
        return counts;
    }
}
