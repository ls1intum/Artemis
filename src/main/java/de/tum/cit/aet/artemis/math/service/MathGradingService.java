package de.tum.cit.aet.artemis.math.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.grader.GraderRegistry;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.grader.HintSuggestion;
import de.tum.cit.aet.artemis.math.grader.MathGrader;
import de.tum.cit.aet.artemis.math.grader.ReachabilityReport;
import de.tum.cit.aet.artemis.math.grader.RewriteChainGrader;

/**
 * Dispatch entry-point for math grading.
 * <p>
 * Selects the appropriate {@link MathGrader} based on the exercise's {@link MathExercise#getGraderType()}
 * and forwards the call. Existing in-process callers (resources, tests) use the legacy
 * {@code gradeSubmission} API; new code may go directly through {@link GraderRegistry}.
 */
@Conditional(MathEnabled.class)
@Lazy
@Service
public class MathGradingService {

    private final GraderRegistry graderRegistry;

    public MathGradingService(GraderRegistry graderRegistry) {
        this.graderRegistry = graderRegistry;
    }

    /**
     * Grades a submission, dispatching to the grader configured on the exercise.
     *
     * @param exercise   the exercise being graded
     * @param submission the student's submission
     * @return score in [0, 100]
     */
    public double gradeSubmission(MathExercise exercise, MathSubmission submission) {
        GraderType type = exercise.getGraderType() == null ? GraderType.REWRITE_CHAIN : exercise.getGraderType();
        return graderRegistry.getGrader(type).grade(exercise, submission).score();
    }

    /**
     * Asks the exercise's grader for hint suggestions at the current state.
     *
     * @param exercise     the exercise being worked on
     * @param currentState the student's current math state
     * @return ranked suggestions, possibly empty
     */
    public List<HintSuggestion> suggestHints(MathExercise exercise, MathNode currentState) {
        GraderType type = exercise.getGraderType() == null ? GraderType.REWRITE_CHAIN : exercise.getGraderType();
        return graderRegistry.getGrader(type).suggestHints(exercise, currentState);
    }

    /**
     * Asks the exercise's grader whether the target is automatically reachable.
     *
     * @param exercise the exercise to analyse
     * @return reachability report, or empty if the grader does not support this check
     */
    public Optional<ReachabilityReport> verifyReachability(MathExercise exercise) {
        GraderType type = exercise.getGraderType() == null ? GraderType.REWRITE_CHAIN : exercise.getGraderType();
        return graderRegistry.getGrader(type).verifyReachability(exercise);
    }

    /**
     * Re-exposes single-step rule application for callers that still operate on the rewrite-chain
     * engine directly. Only meaningful for the {@link GraderType#REWRITE_CHAIN} grader; throws
     * {@link UnsupportedOperationException} if another grader is configured.
     *
     * @param tree the current math tree
     * @param path the index-path to the target node
     * @param rule the rule to apply
     * @return the rewritten tree, or empty if the pattern or any constraint rejects the rule
     */
    public Optional<MathNode> applyRule(MathNode tree, List<Integer> path, RewriteRule rule) {
        MathGrader grader = graderRegistry.getGrader(GraderType.REWRITE_CHAIN);
        if (grader instanceof RewriteChainGrader rcg) {
            return rcg.applyRule(tree, path, rule);
        }
        throw new UnsupportedOperationException("applyRule is only available on the rewrite-chain grader");
    }
}
