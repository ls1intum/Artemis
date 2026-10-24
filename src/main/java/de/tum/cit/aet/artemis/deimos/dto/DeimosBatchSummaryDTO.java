package de.tum.cit.aet.artemis.deimos.dto;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public record DeimosBatchSummaryDTO(String runId, String triggerType, String scope, ZonedDateTime from, ZonedDateTime to, long totalCandidates, long analyzed,
        long manipulationAttemptCount, long maliciousCount, long incompletelyCheckedCount, long benignCount, long failed, List<ParticipationAnalysis> analyzedParticipations,
        List<FailedAnalysis> failedAnalyses) {

    /**
     * Participation id of a {@link FailedAnalysis} that stands for the run as a whole rather than for one participation.
     * The completion email template recognises it by {@code participationId <= 0}.
     */
    public static final long BATCH_LEVEL_FAILURE_PARTICIPATION_ID = 0;

    /**
     * A participation that received a verdict.
     *
     * @param participationId the participation id
     * @param exerciseId      the exercise id, used for the deep link
     * @param verdict         the reported verdict
     * @param llmMalicious    the model's classification, or {@code null} when no verdict could be obtained from it
     * @param rationale       the model's rationale, or {@code null} when no verdict could be obtained from it
     * @param failureType     why no verdict could be obtained from the model, or {@code null} when there is one; only set
     *                            for a {@link DeimosVerdict#MANIPULATION_ATTEMPT}, which holds without the model
     * @param findings        the first imitated fence markers found
     * @param findingCount    the total number of distinct findings
     * @param omissions       the first pieces of evidence left out of the payload
     * @param omissionCount   the total number of distinct omissions
     */
    public record ParticipationAnalysis(long participationId, long exerciseId, DeimosVerdict verdict, @Nullable Boolean llmMalicious, @Nullable String rationale,
            @Nullable DeimosFailureType failureType, List<DeimosManipulationFinding> findings, long findingCount, List<DeimosOmission> omissions, long omissionCount) {
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
        DeimosFailureType batchLevelType = DeimosFailureType.OTHER;
        for (FailedAnalysis failedAnalysis : failedAnalyses) {
            DeimosFailureType type = failedAnalysis.failureType() != null ? failedAnalysis.failureType() : DeimosFailureType.OTHER;
            counts.merge(type, 1L, Long::sum);
            if (failedAnalysis.participationId() <= BATCH_LEVEL_FAILURE_PARTICIPATION_ID) {
                batchLevelType = type;
            }
        }

        // A run that aborts reports every participation it did not reach as failed but records a single batch-level
        // detail for them. Without this reconciliation the email would show "Failed: 200" next to a breakdown adding up
        // to 1, which reads as though 199 participations were fine. The participations not reached share the reason the
        // run stopped, so they are counted under the batch-level entry's category.
        long accountedFor = counts.values().stream().mapToLong(Long::longValue).sum();
        if (failed > accountedFor) {
            counts.merge(batchLevelType, failed - accountedFor, Long::sum);
        }
        return counts;
    }
}
