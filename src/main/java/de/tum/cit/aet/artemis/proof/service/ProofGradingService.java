package de.tum.cit.aet.artemis.proof.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.proof.config.ProofEnabled;
import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;
import de.tum.cit.aet.artemis.proof.grader.GraderRegistry;
import de.tum.cit.aet.artemis.proof.grader.GraderType;
import de.tum.cit.aet.artemis.proof.grader.HintSuggestion;
import de.tum.cit.aet.artemis.proof.grader.ProofGrader;
import de.tum.cit.aet.artemis.proof.grader.ReachabilityReport;
import de.tum.cit.aet.artemis.proof.grader.RewriteChainGrader;

/**
 * Dispatch entry-point for proof grading.
 * <p>
 * Selects the appropriate {@link ProofGrader} based on the exercise's {@link ProofExercise#getGraderType()}
 * and forwards the call. Existing in-process callers (resources, tests) use the legacy
 * {@code gradeSubmission} API; new code may go directly through {@link GraderRegistry}.
 */
@Conditional(ProofEnabled.class)
@Lazy
@Service
public class ProofGradingService {

    private final GraderRegistry graderRegistry;

    public ProofGradingService(GraderRegistry graderRegistry) {
        this.graderRegistry = graderRegistry;
    }

    /**
     * Grades a submission, dispatching to the grader configured on the exercise.
     *
     * @param exercise   the exercise being graded
     * @param submission the student's submission
     * @return score in [0, 100]
     */
    public double gradeSubmission(ProofExercise exercise, ProofSubmission submission) {
        GraderType type = exercise.getGraderType() == null ? GraderType.REWRITE_CHAIN : exercise.getGraderType();
        return graderRegistry.getGrader(type).grade(exercise, submission).score();
    }

    /**
     * Asks the exercise's grader for hint suggestions at the current state.
     *
     * @param exercise     the exercise being worked on
     * @param currentState the student's current proof state
     * @return ranked suggestions, possibly empty
     */
    public List<HintSuggestion> suggestHints(ProofExercise exercise, MathNode currentState) {
        GraderType type = exercise.getGraderType() == null ? GraderType.REWRITE_CHAIN : exercise.getGraderType();
        return graderRegistry.getGrader(type).suggestHints(exercise, currentState);
    }

    /**
     * Asks the exercise's grader whether the target is automatically reachable.
     *
     * @param exercise the exercise to analyse
     * @return reachability report, or empty if the grader does not support this check
     */
    public Optional<ReachabilityReport> verifyReachability(ProofExercise exercise) {
        GraderType type = exercise.getGraderType() == null ? GraderType.REWRITE_CHAIN : exercise.getGraderType();
        return graderRegistry.getGrader(type).verifyReachability(exercise);
    }

    /**
     * Re-exposes single-step rule application for callers that still operate on the rewrite-chain
     * engine directly. Only meaningful for the {@link GraderType#REWRITE_CHAIN} grader; throws
     * {@link UnsupportedOperationException} if another grader is configured.
     *
     * @param tree the current proof tree
     * @param path the index-path to the target node
     * @param rule the rule to apply
     * @return the rewritten tree, or empty if the pattern or any constraint rejects the rule
     */
    public Optional<MathNode> applyRule(MathNode tree, List<Integer> path, RewriteRule rule) {
        ProofGrader grader = graderRegistry.getGrader(GraderType.REWRITE_CHAIN);
        if (grader instanceof RewriteChainGrader rcg) {
            return rcg.applyRule(tree, path, rule);
        }
        throw new UnsupportedOperationException("applyRule is only available on the rewrite-chain grader");
    }
}
