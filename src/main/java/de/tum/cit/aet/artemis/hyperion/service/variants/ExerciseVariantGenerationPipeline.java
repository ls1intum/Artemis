package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;

/**
 * Drives one variant job through the explicit phase state machine (plan Sections 2.1, 2.2, 2.7.2).
 * Type-agnostic: everything type-specific is resolved via {@link VariantTypeRegistry} into the five
 * capability adapters (Section 2.3).
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantGenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantGenerationPipeline.class);

    /** Verify-iteration budget: 1 initial transform + up to (N-1) repair rounds (plan Section 2.5, ≈3–5). */
    private static final int MAX_VERIFY_ATTEMPTS = 3;

    /** Re-prompts for malformed planner output before FAILED (plan Section 6, row 2). */
    private static final int MAX_PLANNING_RETRIES = 2;

    // TODO (Sonnet): Token budget per job, tracked via LLMTokenUsageService (plan Sections 2.5 and 7); currently
    // only the iteration budget is enforced.
    private static final long TOKEN_BUDGET = 500_000;

    /** Internal control-flow signal for cooperative cancellation (plan Section 5.2). */
    private static class JobCancelledException extends RuntimeException {
    }

    /** Internal signal for hard phase failures that should end the job as FAILED (plan Section 6). */
    private static class PhaseFailedException extends RuntimeException {

        PhaseFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        PhaseFailedException(String message) {
            super(message);
        }
    }

    private final VariantTypeRegistry typeRegistry;

    private final VariantAgentLoopRunner agentLoopRunner;

    private final ExerciseVariantJobService jobService;

    private final HyperionPromptTemplateService templateService;

    private final ExerciseRepository exerciseRepository;

    private final ExerciseDeletionService exerciseDeletionService;

    @Nullable
    private final ChatClient chatClient;

    public ExerciseVariantGenerationPipeline(VariantTypeRegistry typeRegistry, VariantAgentLoopRunner agentLoopRunner, ExerciseVariantJobService jobService,
            HyperionPromptTemplateService templateService, ExerciseRepository exerciseRepository, ExerciseDeletionService exerciseDeletionService,
            @Nullable ChatClient chatClient) {
        this.typeRegistry = typeRegistry;
        this.agentLoopRunner = agentLoopRunner;
        this.jobService = jobService;
        this.templateService = templateService;
        this.exerciseRepository = exerciseRepository;
        this.exerciseDeletionService = exerciseDeletionService;
        this.chatClient = chatClient;
    }

    /**
     * Runs the whole pipeline for one job (state diagram in plan Section 2.7.2). Called only from
     * {@code ExerciseVariantTaskService.runJobAsync} (@Async). All terminal transitions
     * (COMPLETED / DRAFT_WITH_WARNINGS / FAILED / CANCELLED) happen in here.
     *
     * @param job the claimed job (already stored in the Hazelcast map)
     */
    public void run(VariantJob job) {
        String jobId = job.getJobId();
        Exercise variant = null;
        try {
            VariantTypeAdapters adapters = typeRegistry.resolve(job.getExerciseType());
            Exercise source = exerciseRepository.findByIdElseThrow(job.getSourceExerciseId());

            // --- ANALYZING ---------------------------------------------------------------------------------
            checkCancelled(jobId);
            jobService.updatePhase(jobId, VariantJobPhase.ANALYZING);
            String sourceContext = runPhase(VariantJobPhase.ANALYZING, () -> adapters.renderContext(source));
            jobService.recordStepOutput(jobId, VariantJobPhase.ANALYZING,
                    new StepOutput("Rendered source exercise context (" + sourceContext.length() + " characters)", truncate(sourceContext), Instant.now()));

            // --- PLANNING ----------------------------------------------------------------------------------
            checkCancelled(jobId);
            jobService.updatePhase(jobId, VariantJobPhase.PLANNING);
            ChangePlan plan = planChanges(job, sourceContext);
            jobService.recordChangePlan(jobId, plan);
            // Also update the local copy: the provisioner reads the plan (variant title) from the job it receives.
            job.setChangePlan(plan);
            jobService.recordStepOutput(jobId, VariantJobPhase.PLANNING, new StepOutput(
                    "Planned \"" + plan.variantTitle() + "\" with " + plan.intendedChanges().size() + " intended change(s) and " + plan.invariants().size() + " invariant(s)",
                    renderPlan(plan), Instant.now()));

            // --- PROVISIONING ------------------------------------------------------------------------------
            checkCancelled(jobId);
            jobService.updatePhase(jobId, VariantJobPhase.PROVISIONING);
            variant = runPhase(VariantJobPhase.PROVISIONING, () -> adapters.provision(source, job.getRequest(), job));
            jobService.recordVariantExerciseId(jobId, variant.getId());
            jobService.recordStepOutput(jobId, VariantJobPhase.PROVISIONING,
                    new StepOutput("Provisioned variant exercise \"" + variant.getTitle() + "\"", "Exercise id: " + variant.getId(), Instant.now()));

            // --- TRANSFORMING / VERIFYING / REPAIRING loop -------------------------------------------------
            VerificationReport report = transformAndVerify(job, adapters, variant, plan);

            // --- FINALIZING --------------------------------------------------------------------------------
            // Cannot be cancelled from here on (plan Section 5.2) — the last cancel window closed above.
            jobService.updatePhase(jobId, VariantJobPhase.FINALIZING);
            Exercise finalVariant = variant;
            runPhase(VariantJobPhase.FINALIZING, () -> {
                adapters.finalizeVariant(finalVariant, job.getRequest());
                return null;
            });

            List<String> warnings = report.passed() ? List.of() : report.findings().stream().map(finding -> finding.gate() + ": " + finding.message()).toList();
            jobService.complete(jobId, variant.getId(), warnings);
            log.info("Variant generation job {} finished with {} for exercise {} -> variant {}", jobId, warnings.isEmpty() ? "COMPLETED" : "DRAFT_WITH_WARNINGS",
                    job.getSourceExerciseId(), variant.getId());
        }
        catch (JobCancelledException cancelled) {
            cleanupProvisionedVariant(variant, jobId);
            jobService.markCancelled(jobId);
            log.info("Variant generation job {} cancelled (exercise {})", jobId, job.getSourceExerciseId());
        }
        catch (PhaseFailedException failure) {
            // Hard-failure policy (plan Section 6): delete any half-created exercise, then FAILED.
            cleanupProvisionedVariant(variant, jobId);
            jobService.fail(jobId, failure.getMessage());
            log.warn("Variant generation job {} failed: {}", jobId, failure.getMessage(), failure.getCause());
        }
    }

    /**
     * The bounded transform → verify → repair loop (plan Sections 2.5 and 2.7.2). Returns the last
     * verification report; a non-passing report after budget exhaustion leads to DRAFT_WITH_WARNINGS —
     * the variant is kept as a flagged draft, never silently deleted (plan Sections 1 and 2.6).
     */
    private VerificationReport transformAndVerify(VariantJob job, VariantTypeAdapters adapters, Exercise variant, ChangePlan plan) {
        String jobId = job.getJobId();
        VariantAgentLoopRunner.AgentBudgets budgets = new VariantAgentLoopRunner.AgentBudgets(MAX_VERIFY_ATTEMPTS, TOKEN_BUDGET);
        String transformTemplate = transformPromptTemplate(job);
        VerificationReport report = null;

        for (int attempt = 1; attempt <= MAX_VERIFY_ATTEMPTS; attempt++) {
            checkCancelled(jobId);
            VariantJobPhase agentPhase = attempt == 1 ? VariantJobPhase.TRANSFORMING : VariantJobPhase.REPAIRING;
            jobService.updatePhase(jobId, agentPhase);
            jobService.recordAttempt(jobId, attempt, MAX_VERIFY_ATTEMPTS, attempt == 1 ? "Applying the change plan" : "Repairing verification findings");

            List<ToolCallback> tools = adapters.createTools(variant, job);
            // Repair rounds receive the previous round's findings as the closed-loop repair signal (plan Section 2.5).
            VerificationReport repairFeedback = report;
            VariantAgentLoopRunner.AgentResult agentResult = runPhase(agentPhase, () -> agentLoopRunner.runLoop(plan, tools, budgets, job, repairFeedback, transformTemplate));
            jobService.recordStepOutput(jobId, agentPhase, new StepOutput("Agent round " + attempt + "/" + MAX_VERIFY_ATTEMPTS + " finished",
                    agentResult.finishSummary() != null ? truncate(agentResult.finishSummary()) : "(no summary)", Instant.now()));

            checkCancelled(jobId);
            jobService.updatePhase(jobId, VariantJobPhase.VERIFYING);
            report = runPhase(VariantJobPhase.VERIFYING, () -> adapters.verify(variant, plan));
            jobService.recordStepOutput(jobId, VariantJobPhase.VERIFYING,
                    new StepOutput(report.passed() ? "All gates green" : report.findings().size() + " finding(s) — attempt " + attempt + "/" + MAX_VERIFY_ATTEMPTS,
                            renderReport(report), Instant.now()));

            if (report.passed()) {
                return report;
            }
        }
        return report;
    }

    /**
     * PLANNING: one structured LLM call producing the {@link ChangePlan} (plan Section 2.4), with the
     * malformed-output policy of plan Section 6 row 2: the conversion error is fed back to the model, at most
     * {@value MAX_PLANNING_RETRIES} re-prompts, then the job fails.
     */
    private ChangePlan planChanges(VariantJob job, String sourceContext) {
        if (chatClient == null) {
            throw new PhaseFailedException("Failed in PLANNING: AI chat client is not configured");
        }
        var outputConverter = new BeanOutputConverter<>(ChangePlan.class);
        String systemPrompt = templateService.render(planPromptTemplate(job), promptVariables(job, sourceContext));
        String userMessage = "Produce the change plan for the requested variant." + "\n\n" + outputConverter.getFormat();

        String lastError = null;
        for (int attempt = 0; attempt <= MAX_PLANNING_RETRIES; attempt++) {
            checkCancelled(job.getJobId());
            try {
                String repromptSuffix = lastError == null ? "" : "\n\nYour previous output was invalid: " + lastError + "\nProduce a corrected change plan.";
                ChangePlan plan = chatClient.prompt().system(systemPrompt).user(userMessage + repromptSuffix).call().entity(outputConverter);
                validatePlan(plan);
                return plan;
            }
            catch (JobCancelledException cancelled) {
                throw cancelled;
            }
            catch (Exception e) {
                lastError = e.getMessage();
                log.warn("Planner output invalid for job {} (attempt {}/{}): {}", job.getJobId(), attempt + 1, MAX_PLANNING_RETRIES + 1, lastError);
            }
        }
        throw new PhaseFailedException("Failed in PLANNING: planner produced no valid change plan after " + (MAX_PLANNING_RETRIES + 1) + " attempts (" + lastError + ")");
    }

    private void validatePlan(ChangePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("empty planner output");
        }
        if (plan.variantTitle() == null || plan.variantTitle().isBlank()) {
            throw new IllegalArgumentException("variantTitle must not be blank");
        }
        if (plan.problemStatement() == null || plan.problemStatement().isBlank()) {
            throw new IllegalArgumentException("problemStatement must not be blank");
        }
        if (plan.intendedChanges() == null || plan.intendedChanges().isEmpty()) {
            throw new IllegalArgumentException("intendedChanges must not be empty");
        }
    }

    private Map<String, String> promptVariables(VariantJob job, String sourceContext) {
        Map<String, String> variables = new HashMap<>();
        variables.put("sourceContext", sourceContext);
        variables.put("targetDifficulty", job.getRequest().targetDifficulty() != null ? job.getRequest().targetDifficulty().name() : "unchanged");
        variables.put("domainText", orDefault(job.getRequest().domainText(), "unchanged"));
        variables.put("additionalInstructions", orDefault(job.getRequest().additionalInstructions(), "none"));
        return variables;
    }

    private String planPromptTemplate(VariantJob job) {
        return switch (job.getExerciseType()) {
            case PROGRAMMING -> "prompts/hyperion/variants/plan_programming.st";
            case QUIZ -> "prompts/hyperion/variants/plan_quiz.st";
            default -> throw new PhaseFailedException("Failed in PLANNING: unsupported exercise type " + job.getExerciseType());
        };
    }

    private String transformPromptTemplate(VariantJob job) {
        return switch (job.getExerciseType()) {
            case PROGRAMMING -> "prompts/hyperion/variants/transform_programming_system.st";
            case QUIZ -> "prompts/hyperion/variants/transform_quiz_system.st";
            default -> throw new PhaseFailedException("Failed in TRANSFORMING: unsupported exercise type " + job.getExerciseType());
        };
    }

    /**
     * Cooperative cancellation check — called at every phase boundary; the agent loop additionally checks
     * between tool rounds (plan Section 5.2: never mid-LLM-call or mid-build).
     */
    private void checkCancelled(String jobId) {
        if (jobService.isCancelRequested(jobId)) {
            throw new JobCancelledException();
        }
    }

    /**
     * Deletes a provisioned half-exercise via the existing deletion service — repos and build plans are
     * cleaned up on the same path as regular exercise deletion (plan Section 6, hard-failure and cancel rows).
     */
    private void cleanupProvisionedVariant(@Nullable Exercise variant, String jobId) {
        if (variant == null || variant.getId() == null) {
            return;
        }
        try {
            exerciseDeletionService.delete(variant.getId(), true);
        }
        catch (Exception e) {
            // Never mask the original failure/cancellation with a cleanup error — flag it for manual cleanup.
            log.error("Failed to clean up provisioned variant exercise {} for job {}", variant.getId(), jobId, e);
        }
    }

    /** Wraps a phase body so any exception carries the phase name into the FAILED detail ("failed in VERIFYING"). */
    private <T> T runPhase(VariantJobPhase phase, PhaseBody<T> body) {
        try {
            return body.execute();
        }
        catch (JobCancelledException | PhaseFailedException controlFlow) {
            throw controlFlow;
        }
        catch (Exception e) {
            throw new PhaseFailedException("Failed in " + phase + ": " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface PhaseBody<T> {

        T execute() throws Exception;
    }

    private String renderPlan(ChangePlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("Title: ").append(plan.variantTitle()).append("\n\nIntended changes:\n");
        plan.intendedChanges().forEach(change -> builder.append("- ").append(change).append('\n'));
        builder.append("\nInvariants:\n");
        plan.invariants().forEach(invariant -> builder.append("- ").append(invariant).append('\n'));
        builder.append("\nProblem statement:\n").append(plan.problemStatement());
        return truncate(builder.toString());
    }

    private String renderReport(VerificationReport report) {
        if (report.passed()) {
            return "All verification gates passed.";
        }
        StringBuilder builder = new StringBuilder();
        report.findings().forEach(finding -> builder.append("[").append(finding.gate()).append("] ").append(finding.message()).append('\n'));
        return truncate(builder.toString());
    }

    private static String truncate(String text) {
        final int maxLength = 100_000; // keep Hazelcast entries bounded (StepOutput note)
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n[truncated]";
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
