package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Attaches automated quality findings to a saved exercise so instructors can review, discuss, and adapt it in the normal editor. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationReviewService {

    private static final Logger log = LoggerFactory.getLogger(GenerationReviewService.class);

    private static final ConsistencyIssueCategory REVIEW_CATEGORY = ConsistencyIssueCategory.GENERATION_REVIEW_REQUIRED;

    private static final int ANCHOR_LINE = 1;

    /** Returned when the exercise was saved but its review comments could not be attached. */
    public static final int REVIEW_COMMENTS_FAILED = -1;

    private final ExerciseReviewService exerciseReviewService;

    private final ExerciseEditorSyncService exerciseEditorSyncService;

    public GenerationReviewService(ExerciseReviewService exerciseReviewService, ExerciseEditorSyncService exerciseEditorSyncService) {
        this.exerciseReviewService = exerciseReviewService;
        this.exerciseEditorSyncService = exerciseEditorSyncService;
    }

    /**
     * Best effort: the exercise is already saved, so a failure to attach findings must neither misreport nor roll that save back.
     *
     * @param exercise the saved exercise that receives the review threads
     * @param user     the instructor recorded as the review-thread author
     * @param report   the automated quality findings to surface
     * @return the number of created threads, zero for an empty report, or {@link #REVIEW_COMMENTS_FAILED} when attachment failed
     */
    public int attachFindings(ProgrammingExercise exercise, User user, SpecFidelityReport report) {
        return attachFindingsInternal(exercise, user, report, null, Map.of());
    }

    public int attachFindings(ProgrammingExercise exercise, User user, SpecFidelityReport report, long savedExerciseVersionId, Map<RepositoryType, String> savedRepositoryHeads) {
        return attachFindingsInternal(exercise, user, report, savedExerciseVersionId, savedRepositoryHeads);
    }

    private int attachFindingsInternal(ProgrammingExercise exercise, User user, SpecFidelityReport report, Long savedExerciseVersionId,
            Map<RepositoryType, String> savedRepositoryHeads) {
        List<ConsistencyIssueDTO> findings = toReviewFindings(report);
        if (findings.isEmpty()) {
            return 0;
        }
        try {
            List<CommentThread> createdThreads = savedExerciseVersionId == null ? exerciseReviewService.createConsistencyCheckThreads(exercise.getId(), findings, user)
                    : exerciseReviewService.createConsistencyCheckThreads(exercise.getId(), findings, user, savedExerciseVersionId, savedRepositoryHeads);
            for (CommentThread thread : createdThreads) {
                CommentThreadDTO createdThread = new CommentThreadDTO(thread, CommentDTO.fromThread(thread));
                exerciseEditorSyncService.broadcastReviewThreadUpdate(exercise.getId(), ReviewThreadSyncDTO.threadCreated(createdThread));
            }
            log.info("Attached {} generation review thread(s) to exercise {}", createdThreads.size(), exercise.getId());
            return createdThreads.size();
        }
        catch (RuntimeException e) {
            log.warn("Could not attach generation review threads to exercise {}", exercise.getId(), e);
            return REVIEW_COMMENTS_FAILED;
        }
    }

    static List<ConsistencyIssueDTO> toReviewFindings(SpecFidelityReport report) {
        List<ConsistencyIssueDTO> findings = new ArrayList<>();
        for (SpecFidelityReport.Finding finding : report.findings()) {
            String title = switch (finding.kind()) {
                case MECHANICS_LEAK -> "Grader-mechanics phrasing in the student-facing problem statement: \"" + finding.requirement() + "\"";
                case MISSING_WORKED_EXAMPLE -> "Important behaviour may benefit from a concrete worked example: \"" + finding.requirement() + "\"";
                case INVENTED_REQUIREMENT -> "Requirement not asked for by the brief (confirm or remove): \"" + finding.requirement() + "\"";
                case UNREQUESTED_ADAPTATION_CHANGE -> "Adaptation changed content outside the requested scope: \"" + finding.requirement() + "\"";
                case REQUESTED_ADAPTATION_CHANGE_MISSING -> "Requested adaptation change is missing or incomplete: \"" + finding.requirement() + "\"";
                case ADAPTATION_SCOPE_REVIEW_UNAVAILABLE -> "Adaptation scope could not be verified automatically";
                case UNCOVERED_REQUIREMENT -> "Possible coverage gap against the brief: \"" + finding.requirement() + "\"";
                case MISSING_FAILURE_MESSAGE -> "Graded tests give no failure message, so a failing student sees only \"expected X but was Y\": " + finding.requirement();
                case CONTRACT_CONTRADICTION -> "Generated artifacts contradict the student-facing contract: \"" + finding.requirement() + "\"";
                case HIDDEN_GRADED_REQUIREMENT -> "Graded requirement is not discoverable by students: \"" + finding.requirement() + "\"";
                case WEAK_TEST_ORACLE -> "Generated tests allow a plausible incorrect implementation: \"" + finding.requirement() + "\"";
                case EXECUTABLE_WEAK_TEST_ORACLE -> "Execution proved that the generated tests allow an incorrect implementation: \"" + finding.requirement() + "\"";
                case TEMPLATE_QUALITY_GAP -> "Student task and starter scaffold need alignment: \"" + finding.requirement() + "\"";
                case UNENFORCEABLE_TECHNIQUE_RULE -> "Students can pass without using the technique the exercise teaches: " + finding.requirement();
                case CONTRACT_WITNESS_AVAILABLE -> "A ready-to-adopt test for a specification rule, already passing against the reference solution: " + finding.requirement();
                case QUALITY_REVIEW_UNAVAILABLE -> "Generated exercise quality could not be reviewed automatically";
                case SPECIFICATION_REVIEW_FINDING -> "The compiled exercise specification still has an unresolved review finding: " + finding.requirement();
            };
            Severity severity = finding.isBlocking() ? Severity.HIGH : Severity.MEDIUM;
            ArtifactLocationDTO location = new ArtifactLocationDTO(ArtifactType.PROBLEM_STATEMENT, "", ANCHOR_LINE, ANCHOR_LINE);
            findings.add(new ConsistencyIssueDTO(severity, REVIEW_CATEGORY, title, finding.detail(), List.of(location)));
        }
        return findings;
    }
}
