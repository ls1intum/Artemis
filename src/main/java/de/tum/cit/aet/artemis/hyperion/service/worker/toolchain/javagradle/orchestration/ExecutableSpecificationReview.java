package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic.SpecificationReview;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult;

/** Rechecks a contract once its assessment exists, before a passing grader can hide a specification defect. State belongs to one staged invocation. */
final class ExecutableSpecificationReview {

    private static final Logger log = LoggerFactory.getLogger(ExecutableSpecificationReview.class);

    record Result(StageCheckResult gate, @Nullable SpecificationReview review, List<String> unresolvedFindings) {
    }

    @Nullable
    private final SpecFidelityCritic reviewer;

    private final String brief;

    private final AgentTranscriptWriter transcriptWriter;

    private final long exerciseId;

    @Nullable
    private final Consumer<ChatResponse> usageSink;

    private final BooleanSupplier cancelled;

    @Nullable
    private final Consumer<String> progress;

    private final Set<String> reviewedSpecifications = new HashSet<>();

    ExecutableSpecificationReview(@Nullable SpecFidelityCritic reviewer, String brief, AgentTranscriptWriter transcriptWriter, long exerciseId,
            @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled, @Nullable Consumer<String> progress) {
        this.reviewer = reviewer;
        this.brief = brief;
        this.transcriptWriter = transcriptWriter;
        this.exerciseId = exerciseId;
        this.usageSink = usageSink;
        this.cancelled = cancelled;
        this.progress = progress;
    }

    Result check(GenerationStage stage, @Nullable String specification, @Nullable String concept, @Nullable SpecificationReview previousReview, StageCheckResult gate,
            @Nullable AgentVerifyReport firstReferenceFailure, boolean redesignAvailable) {
        Result unchanged = new Result(gate, null, List.of());
        boolean executedAssertionFailure = firstReferenceFailure != null && !firstReferenceFailure.solutionPassed() && firstReferenceFailure.solutionTests() > 0
                && !firstReferenceFailure.solutionFailureEvidence().isEmpty();
        if (stage != GenerationStage.TESTS || reviewer == null || specification == null || gate.report() == null || gate.contractRedesignRequired()
                || !gate.passed() && !executedAssertionFailure || cancelled.getAsBoolean() || !reviewedSpecifications.add(specification)) {
            return unchanged;
        }
        if (progress != null) {
            progress.accept("Checking the executable assessment against the instructor's learning contract");
        }
        SpecificationReview review;
        try {
            review = reviewer.reviewSpecificationWithExecution(brief, concept, specification, previousReview, observations(gate.report(), firstReferenceFailure), usageSink,
                    cancelled);
        }
        catch (RuntimeException exception) {
            log.warn("Execution-informed specification review failed for exercise {} ({})", exerciseId, exception.getClass().getSimpleName());
            return new Result(gate, null, List.of("The execution-informed specification review did not complete; contract consistency and learning-goal assessment need review."));
        }
        if (review == null || !review.complete()) {
            String detail = review == null ? "No complete verdict was returned." : review.auditSummary();
            if (review != null) {
                transcriptWriter.writeAudit(exerciseId, "execution-spec-review", SpecificationReviewPrompts.specificationReviewAudit(review));
            }
            List<String> findings = new ArrayList<>(List.of("The execution-informed specification review was incomplete: " + detail));
            if (review != null) {
                java.util.stream.Stream.concat(review.findings().stream(), review.riskHistory().stream()).distinct()
                        .map(risk -> "Retained contract concern requiring adjudication after incomplete review: " + risk).forEach(findings::add);
            }
            return new Result(gate, null, List.copyOf(findings));
        }
        transcriptWriter.writeAudit(exerciseId, "execution-spec-review", SpecificationReviewPrompts.specificationReviewAudit(review));
        if (review.accepted()) {
            return new Result(gate, review, List.of());
        }
        if (redesignAvailable) {
            return new Result(StageCheckResult.specificationRedesignRequired(String.join("\n", review.findings()), gate.report()), review, List.of());
        }
        // An optional contract review cannot turn a failed build into a pass, or discard a verified first draft merely because its correction budget is spent.
        return new Result(gate, review, review.findings());
    }

    private static String observations(AgentVerifyReport current, @Nullable AgentVerifyReport firstFailure) {
        String history = firstFailure == null ? "No earlier executed reference assertion failure was recorded."
                : AgentVerifyReport.renderFailureEvidence("First reference build", firstFailure.solutionFailureEvidence());
        return "REFERENCE TEST FAILURE HISTORY (before later author repairs):\n" + history + "\nCURRENT NATIVE ASSESSMENT: reference tests=" + current.solutionTests()
                + ", reference passed=" + current.solutionPassed() + ", starter compiled=" + current.templateCompiled() + ", starter policy passed=" + current.templateFailed()
                + ".\nDiscovered assessment names (first 40 of " + current.exactTestNames().size() + "): " + current.exactTestNames().stream().limit(40).toList()
                + "\nZero-credit preservation checks (first 40): " + current.preservationTestNames().stream().limit(40).toList()
                + "\nThese names describe executed checks, not evidence that a learner goal is assessed. Inspect the approved ownership and grading seams against each frozen goal.";
    }
}
