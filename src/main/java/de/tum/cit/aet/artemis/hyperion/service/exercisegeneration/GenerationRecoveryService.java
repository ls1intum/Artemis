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
 * Recovers a near-miss exercise-generation run instead of discarding it.
 * <p>
 * When the agent produced a complete-but-not-verified exercise (rejected after the attempt budget, or otherwise not accepted), the binary
 * "nothing was saved" outcome throws away real instructor-usable work and tells them only that it failed. This service closes that gap: it
 * persists the best-effort artifact as a <em>draft</em> through the same {@link GenerationPersistenceService} path an accepted run uses, and
 * then translates every authoritative-verification finding (plus the agent's final note) into {@code CONSISTENCY_CHECK} review-comment threads
 * on the exercise, reusing the exact path the manual consistency check uses ({@link ExerciseReviewService#createConsistencyCheckThreads}). The
 * instructor opens the exercise and sees the partially-correct draft together with a precise, actionable checklist of what still needs fixing.
 * <p>
 * <strong>Safety boundary.</strong> A recovered draft is never presented as a verified, accepted exercise. The terminal event carries the
 * distinct {@code NEEDS_REVIEW} verdict (never {@code SUCCESS}), and the draft always carries at least one review comment enumerating the gaps;
 * an accepted run produces zero generation-recovery review comments. Persisting the files makes the work durable and editable (it is a normal
 * git commit + exercise version the instructor can revert), but the review comments are the unambiguous signal that the exercise is unfinished
 * and must be completed by a human before it is used for grading. Recovery never runs on a cancelled or hard-error run (the workspace there is
 * unusable), only on a clean non-accepted terminal state with extractable files.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class GenerationRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(GenerationRecoveryService.class);

    /**
     * Category chip carried by every recovery finding. The verification gates do not map onto the structural mismatch categories, so the most
     * generic semantic category is used; the human-readable gate text in the description carries the real, actionable detail.
     */
    private static final ConsistencyIssueCategory RECOVERY_CATEGORY = ConsistencyIssueCategory.IDENTIFIER_NAMING_INCONSISTENCY;

    /**
     * Recovery findings anchor to the problem statement at its first line. The problem statement always exists (unlike a repository file path
     * that may not be present), so the review thread reliably lands in the editor regardless of which gate failed; the finding text names the
     * concrete artifact (solution, template, a specific test) it concerns.
     */
    private static final int ANCHOR_LINE = 1;

    /**
     * Sentinel returned by {@link #recover} when the draft was durably persisted but its review-comment threads could not be attached. The caller still emits {@code NEEDS_REVIEW}
     * (the draft exists), never {@code PARTIAL}, but with a degraded message — so a failed annotation never mislabels a saved draft as "nothing was saved".
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
     * @param reviewThreadCount     the number of review-comment threads created (zero if no findings mapped), or {@link #REVIEW_COMMENTS_FAILED} ({@code -1}) when the draft was
     *                                  persisted but its review comments could not be attached
     * @param liveExerciseUntouched {@code true} if the draft was an adapt of an already-working exercise and was diverted to an isolated branch, leaving the live exercise
     *                                  byte-identical (it keeps working and grading as before); {@code false} if the draft was committed to the default branch (a from-scratch
     *                                  target, which had nothing to lose)
     * @param draftBranch           the isolated branch the draft was diverted to when {@code liveExerciseUntouched} is {@code true}; {@code null} otherwise
     */
    public record RecoveryResult(int reviewThreadCount, boolean liveExerciseUntouched, String draftBranch) {
    }

    /**
     * Recovers a non-accepted generation run: persists the best-effort produced files as a draft and creates review-comment threads describing
     * every verification gap so the instructor can finish the exercise instead of losing the work.
     * <p>
     * <strong>Safety boundary (W3 fix).</strong> A non-accepted run must never regress a previously-working exercise. For an adapt of an already-working exercise the draft is
     * BROKEN (it failed verification); committing it onto the live default branch would silently replace a working exercise with a failing one, with no clean restore. So the
     * persistence layer ({@link GenerationPersistenceService#persistRecoveryDraft}) diverts an adapt draft to an isolated branch and leaves the live exercise untouched, while a
     * from-scratch draft (nothing to lose) is still committed to the default branch and edited in place. The {@link RecoveryResult} carries which path was taken so the caller can
     * tell the instructor whether their working exercise was preserved.
     *
     * @param exercise the target exercise (the draft is committed into its repositories or an isolated branch)
     * @param user     the instructor who started the run (commit author and review-comment author)
     * @param outcome  the non-accepted outcome holding the produced files, verification report, and agent note
     * @param jobId    the generation job id, used to name the isolated draft branch for an adapt target
     * @return the recovery result (review-thread count and whether the live exercise was left untouched)
     * @throws RuntimeException only when the persist itself fails (nothing durable was saved); the caller maps that to {@code PARTIAL}
     */
    public RecoveryResult recover(ProgrammingExercise exercise, User user, GenerationOutcome outcome, String jobId) {
        // 1. Persist the best-effort files so the instructor's work survives — WITHOUT regressing a working exercise. For a from-scratch target this commits the sandbox-final tree
        // to the default branch (orphan-mirrored, harness-protected) and records an exercise version, exactly as before. For an adapt of an already-working exercise it diverts the
        // draft to an isolated branch and leaves the live default branch byte-identical, so a failed adapt can never replace a working exercise with a failing one. It does NOT
        // mark
        // the exercise accepted/verified — that distinction is carried by the review comments and the NEEDS_REVIEW terminal event, never by the persisted bytes.
        // A failure HERE means nothing durable was saved, so it propagates and the caller reports PARTIAL ("nothing changed").
        GenerationPersistenceService.RecoveryPersistResult persistResult = persistenceService.persistRecoveryDraft(exercise, user, outcome, jobId);

        // 2. The draft is now durably committed. From this point a failure must NOT be reported as "nothing saved" (the half-commit mislabel): the
        // annotation step below is best-effort. If it throws, we swallow it (logged) and return REVIEW_COMMENTS_FAILED so the caller still emits
        // NEEDS_REVIEW — never PARTIAL — but with an accurate, degraded message telling the instructor to treat the saved draft as unfinished.
        try {
            // Translate the verification findings (and the agent's final note) into review threads on the now-persisted draft.
            List<ConsistencyIssueDTO> findings = toFindings(outcome);
            if (findings.isEmpty()) {
                return new RecoveryResult(0, persistResult.liveExerciseUntouched(), persistResult.draftBranch());
            }
            List<CommentThread> createdThreads = exerciseReviewService.createConsistencyCheckThreads(exercise.getId(), findings);
            // Notify open editors so the review panel updates live, exactly as the manual consistency check does.
            for (CommentThread thread : createdThreads) {
                CommentThreadDTO createdThread = new CommentThreadDTO(thread, CommentDTO.fromThread(thread));
                exerciseEditorSyncService.broadcastReviewThreadUpdate(exercise.getId(), ReviewThreadSyncDTO.threadCreated(createdThread));
            }
            log.info("Recovered generation draft for exercise {} with {} review-comment thread(s) from verification findings", exercise.getId(), createdThreads.size());
            return new RecoveryResult(createdThreads.size(), persistResult.liveExerciseUntouched(), persistResult.draftBranch());
        }
        catch (RuntimeException e) {
            // The draft IS saved; only the review-comment annotation failed. Do not let that masquerade as "nothing saved".
            log.error("Generation draft for exercise {} was persisted but its review comments could not be attached; surfacing as a degraded NEEDS_REVIEW", exercise.getId(), e);
            return new RecoveryResult(REVIEW_COMMENTS_FAILED, persistResult.liveExerciseUntouched(), persistResult.draftBranch());
        }
    }

    /**
     * Builds the review findings for a non-accepted outcome: one {@code CONSISTENCY_CHECK} issue per verification reason, plus one for the
     * agent's final note when it is non-empty. Each issue anchors to the problem statement so it reliably surfaces in the editor.
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
        // Advisory spec-fidelity / coverage gaps (the brief-coverage axis the differential oracle is blind to). These are NON-BLOCKING context for the instructor, not verification
        // gates, so they are MEDIUM (a recovered draft already failed the hard gates above); they never changed the accept/reject verdict.
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
            String title = finding.kind() == SpecFidelityReport.Kind.MECHANICS_LEAK
                    ? "Grader-mechanics phrasing in the student-facing problem statement: \"" + finding.requirement() + "\""
                    : "Possible coverage gap against the brief: \"" + finding.requirement() + "\"";
            findings.add(finding(Severity.MEDIUM, title, finding.detail()));
        }
        return findings;
    }

    /**
     * Surfaces the advisory spec-fidelity findings of an ACCEPTED exercise as review-comment threads, WITHOUT changing its accepted status. The differential oracle accepted the
     * exercise (it is internally sound and is persisted as a normal, verified exercise); these threads are purely advisory context the instructor may act on or dismiss.
     * Best-effort
     * and never throws: a failure to attach advisory comments must not turn a successful generation into a failure, so it is logged and swallowed.
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
            // Advisory only: never let a failed attach turn a successful generation into a failure.
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
