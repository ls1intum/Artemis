package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ReviewThreadSyncDTO;
import de.tum.cit.aet.artemis.exercise.service.ExerciseEditorSyncService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Recovers a near-miss exercise-generation run instead of discarding it: persists the best-effort artifact as a <em>draft</em> through the same
 * {@link GenerationPersistenceService} path an accepted run uses, then translates every verification finding (plus the agent's final note) into
 * {@code CONSISTENCY_CHECK} review-comment threads via the manual consistency-check path ({@link ExerciseReviewService#createConsistencyCheckThreads}).
 * <p>
 * Load-bearing invariant: a recovered draft is never presented as accepted — the terminal verdict is always {@code NEEDS_REVIEW} (never {@code SUCCESS})
 * and the draft always carries at least one review comment. Recovery runs only on a clean non-accepted terminal state with extractable files (never on a
 * cancelled or hard-error run, whose workspace is unusable).
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class GenerationRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(GenerationRecoveryService.class);

    /**
     * Category chip on every recovery finding. The verification gates don't map onto the structural categories, so the most generic one is used; the description carries the
     * detail.
     */
    private static final ConsistencyIssueCategory RECOVERY_CATEGORY = ConsistencyIssueCategory.IDENTIFIER_NAMING_INCONSISTENCY;

    /** Recovery findings anchor to the problem statement's first line: it always exists (unlike a repository file path), so the thread reliably lands in the editor. */
    private static final int ANCHOR_LINE = 1;

    /**
     * Sentinel returned by {@link #recover} when the draft was persisted but its review-comment threads could not be attached. The caller still emits {@code NEEDS_REVIEW} (never
     * {@code PARTIAL}) with a degraded message, so a failed annotation never mislabels a saved draft as "nothing was saved".
     */
    static final int REVIEW_COMMENTS_FAILED = -1;

    private final GenerationPersistenceService persistenceService;

    private final ExerciseReviewService exerciseReviewService;

    private final ExerciseEditorSyncService exerciseEditorSyncService;

    public GenerationRecoveryService(GenerationPersistenceService persistenceService, ExerciseReviewService exerciseReviewService,
            ExerciseEditorSyncService exerciseEditorSyncService) {
        this.persistenceService = persistenceService;
        this.exerciseReviewService = exerciseReviewService;
        this.exerciseEditorSyncService = exerciseEditorSyncService;
    }

    /**
     * The result of recovering a non-accepted generation run.
     *
     * @param reviewThreadCount     the number of review-comment threads created, or {@link #REVIEW_COMMENTS_FAILED} when the draft was persisted but its review comments could not
     *                                  be
     *                                  attached
     * @param liveExerciseUntouched {@code true} if an adapt draft was diverted to an isolated branch leaving the live exercise byte-identical; {@code false} if committed to the
     *                                  default branch (a from-scratch target)
     * @param draftBranch           the isolated branch the draft was diverted to when {@code liveExerciseUntouched} is {@code true}; {@code null} otherwise
     */
    public record RecoveryResult(int reviewThreadCount, boolean liveExerciseUntouched, String draftBranch) {
    }

    /**
     * Recovers a non-accepted generation run: persists the best-effort produced files as a draft (without regressing a working exercise, see
     * {@link GenerationPersistenceService#persistRecoveryDraft}) and creates review-comment threads describing every verification gap so the instructor can finish the exercise
     * instead of losing the work.
     *
     * @param exercise the target exercise (the draft is committed into its repositories or an isolated branch)
     * @param user     the instructor who started the run (commit author and review-comment author)
     * @param outcome  the non-accepted outcome holding the produced files, verification report, and agent note
     * @param jobId    the generation job id, used to name the isolated draft branch for an adapt target
     * @return the recovery result (review-thread count and whether the live exercise was left untouched)
     * @throws RuntimeException only when the persist itself fails (nothing durable was saved); the caller maps that to {@code PARTIAL}
     */
    public RecoveryResult recover(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId) {
        // A persist failure means nothing durable was saved, so it propagates and the caller reports PARTIAL.
        GenerationPersistenceService.RecoveryPersistResult persistResult = persistenceService.persistRecoveryDraft(exercise, user, outcome, jobId);

        // The draft is now committed, so a failed annotation must not be reported as "nothing saved": swallow it and return REVIEW_COMMENTS_FAILED for a degraded NEEDS_REVIEW.
        try {
            List<ConsistencyIssueDTO> findings = toFindings(outcome);
            if (findings.isEmpty()) {
                return new RecoveryResult(0, persistResult.liveExerciseUntouched(), persistResult.draftBranch());
            }
            List<CommentThread> createdThreads = exerciseReviewService.createConsistencyCheckThreads(exercise.getId(), findings);
            // Notify open editors so the review panel updates live, as the manual consistency check does.
            for (CommentThread thread : createdThreads) {
                CommentThreadDTO createdThread = new CommentThreadDTO(thread, CommentDTO.fromThread(thread));
                exerciseEditorSyncService.broadcastReviewThreadUpdate(exercise.getId(), ReviewThreadSyncDTO.threadCreated(createdThread));
            }
            log.info("Recovered generation draft for exercise {} with {} review-comment thread(s) from verification findings", exercise.getId(), createdThreads.size());
            return new RecoveryResult(createdThreads.size(), persistResult.liveExerciseUntouched(), persistResult.draftBranch());
        }
        catch (RuntimeException e) {
            // The draft IS saved; only the annotation failed. Do not let that masquerade as "nothing saved".
            log.error("Generation draft for exercise {} was persisted but its review comments could not be attached; surfacing as a degraded NEEDS_REVIEW", exercise.getId(), e);
            return new RecoveryResult(REVIEW_COMMENTS_FAILED, persistResult.liveExerciseUntouched(), persistResult.draftBranch());
        }
    }

    /**
     * Builds the review findings for a non-accepted outcome: one issue per verification reason, one for the agent's final note when non-empty, plus the advisory spec-fidelity
     * gaps.
     *
     * @param outcome the non-accepted outcome
     * @return the findings to persist as review threads (possibly empty)
     */
    static List<ConsistencyIssueDTO> toFindings(GenerationOutcome outcome) {
        List<ConsistencyIssueDTO> findings = new ArrayList<>();
        VerificationResult verification = outcome.verification();
        if (verification != null) {
            for (String reason : verification.reasons()) {
                if (reason != null && !reason.isBlank()) {
                    findings.add(finding(Severity.HIGH, "Generated draft needs review — verification gap: " + reason.trim(),
                            "Fix the gap above, then re-run exercise generation (or finish the exercise manually) and re-run the consistency check to confirm."));
                }
            }
        }
        String agentNote = outcome.loopResult().finalMessage();
        if (agentNote != null && !agentNote.isBlank()) {
            findings.add(finding(Severity.MEDIUM, "Note from the generation agent: " + agentNote.trim(),
                    "Use the agent's note for context on what was attempted and what remains to be done."));
        }
        // Advisory spec-fidelity / coverage gaps (the brief-coverage axis the oracle is blind to): non-blocking context only, never part of the accept/reject verdict.
        findings.addAll(specFidelityFindings(outcome.specFidelityReport()));
        return findings;
    }

    /**
     * Translates the advisory spec-fidelity report into review findings. Shared by the recovery path (non-accepted draft) and the accepted path, so the same brief-coverage gaps
     * surface to the instructor whether or not the differential oracle accepted the exercise.
     *
     * @param report the advisory spec-fidelity report
     * @return one MEDIUM consistency issue per finding (possibly empty)
     */
    static List<ConsistencyIssueDTO> specFidelityFindings(SpecFidelityReport report) {
        List<ConsistencyIssueDTO> findings = new ArrayList<>();
        for (SpecFidelityReport.Finding finding : report.findings()) {
            String title = switch (finding.kind()) {
                case MECHANICS_LEAK -> "Grader-mechanics phrasing in the student-facing problem statement: \"" + finding.requirement() + "\"";
                case MISSING_WORKED_EXAMPLE -> "Error/edge behaviour without a concrete worked example: \"" + finding.requirement() + "\"";
                case INVENTED_REQUIREMENT -> "Requirement not asked for by the brief (confirm or remove): \"" + finding.requirement() + "\"";
                case UNCOVERED_REQUIREMENT -> "Possible coverage gap against the brief: \"" + finding.requirement() + "\"";
                case MISSING_FAILURE_MESSAGE -> "Graded tests give no failure message, so a failing student sees only \"expected X but was Y\": " + finding.requirement();
            };
            findings.add(finding(Severity.MEDIUM, title, finding.detail()));
        }
        return findings;
    }

    /**
     * Surfaces an ACCEPTED exercise's advisory spec-fidelity findings as review-comment threads WITHOUT changing its accepted status — purely advisory context the instructor may
     * act
     * on or dismiss. Best-effort and never throws: a failed attach must not turn a successful generation into a failure.
     *
     * @param exercise the accepted, persisted exercise
     * @param report   the advisory spec-fidelity report (no threads are created when it is empty)
     * @return the number of advisory threads created (zero when there were no findings or attachment failed)
     */
    public int surfaceAdvisoryFindings(ProgrammingExercise exercise, SpecFidelityReport report) {
        List<ConsistencyIssueDTO> findings = specFidelityFindings(report);
        if (findings.isEmpty()) {
            return 0;
        }
        try {
            List<CommentThread> createdThreads = exerciseReviewService.createConsistencyCheckThreads(exercise.getId(), findings);
            for (CommentThread thread : createdThreads) {
                CommentThreadDTO createdThread = new CommentThreadDTO(thread, CommentDTO.fromThread(thread));
                exerciseEditorSyncService.broadcastReviewThreadUpdate(exercise.getId(), ReviewThreadSyncDTO.threadCreated(createdThread));
            }
            log.info("Attached {} advisory spec-fidelity review thread(s) to accepted exercise {}", createdThreads.size(), exercise.getId());
            return createdThreads.size();
        }
        catch (RuntimeException e) {
            log.warn("Could not attach advisory spec-fidelity review threads to accepted exercise {}; continuing", exercise.getId(), e);
            return 0;
        }
    }

    /**
     * Builds one problem-statement-anchored consistency issue carrying the given description and suggested fix.
     *
     * @param severity     the issue severity chip
     * @param description  the human-readable finding (the real, actionable detail)
     * @param suggestedFix the suggested next step
     * @return a single-location consistency issue anchored to the problem statement
     */
    private static ConsistencyIssueDTO finding(Severity severity, String description, String suggestedFix) {
        ArtifactLocationDTO location = new ArtifactLocationDTO(ArtifactType.PROBLEM_STATEMENT, "", ANCHOR_LINE, ANCHOR_LINE);
        return new ConsistencyIssueDTO(severity, RECOVERY_CATEGORY, description, suggestedFix, List.of(location));
    }
}
