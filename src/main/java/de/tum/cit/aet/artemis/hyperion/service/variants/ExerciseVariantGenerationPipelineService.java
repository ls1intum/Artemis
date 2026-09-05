package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;

/**
 * Drives one variant job through the explicit phase state machine.
 * Type-agnostic: everything type-specific is resolved via {@link VariantTypeRegistryService} into the five
 * capability adapters.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantGenerationPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantGenerationPipelineService.class);

    /**
     * Verify-iteration budget: 1 initial transform + up to (N-1) repair rounds. Builds now only run in
     * VERIFYING (never mid-round inside TRANSFORMING/REPAIRING), so each attempt costs a bounded, fixed amount
     * of CI time instead of an agent-driven, open-ended number of rebuilds. TOKEN_BUDGET below is the actual
     * cost backstop regardless of how many attempts are allowed.
     */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    /**
     * Stuck-repair-loop detection (see {@link #computeEscalationNote}): how many of the most recent rounds'
     * finding-signature sets are compared against the current round's.
     */
    private static final int STUCK_LOOP_WINDOW = 3;

    /**
     * A finding signature recurring in this many of the last {@link #STUCK_LOOP_WINDOW} rounds (this one
     * included) triggers the escalation note — 2 rather than 3 so it can still fire with room left in the
     * {@link #MAX_VERIFY_ATTEMPTS} budget instead of only on the very last round.
     */
    private static final int STUCK_LOOP_REPEAT_THRESHOLD = 2;

    /** Re-prompts for malformed planner output before FAILED. */
    private static final int MAX_PLANNING_RETRIES = 2;

    /**
     * Token budget for the TRANSFORMING/REPAIRING sequence, tracked via LLMTokenUsageService and checked BETWEEN
     * rounds. Not a hard cap: the figure a round reports is a lower bound (see
     * {@code VariantAgentLoopService.extractTotalTokens} — Spring AI's internal tool loop reports usage for its
     * final exchange only). What actually bounds a job is the per-round tool-call budget of each toolset, whose
     * hard stop ends the round's internal loop outright once it is exceeded, times {@link #MAX_VERIFY_ATTEMPTS}.
     */
    private static final long TOKEN_BUDGET = 500_000;

    /** Bound for the cause-chain walk in {@link #leftoverExerciseId}; the real chain is three deep at most. */
    private static final int MAX_CAUSE_CHAIN_DEPTH = 10;

    /** Pipeline id for token-usage traces of the PLANNING call. */
    private static final String PLAN_PIPELINE_ID = "exercise-variant-plan";

    /** Pipeline id for token-usage traces of the failure-summary call. */
    private static final String FAILURE_SUMMARY_PIPELINE_ID = "exercise-variant-failure-summary";

    /** Pipeline id for token-usage traces of the flagged-draft summary call. */
    private static final String WARNING_SUMMARY_PIPELINE_ID = "exercise-variant-warning-summary";

    /** Internal control-flow signal for cooperative cancellation. */
    private static class JobCancelledException extends RuntimeException {
    }

    /** Internal signal for hard phase failures that should end the job as FAILED. */
    private static class PhaseFailedException extends RuntimeException {

        PhaseFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        PhaseFailedException(String message) {
            super(message);
        }
    }

    private final VariantTypeRegistryService typeRegistry;

    private final VariantAgentLoopService agentLoopRunner;

    private final ExerciseVariantJobService jobService;

    private final HyperionPromptTemplateService templateService;

    private final ExerciseRepository exerciseRepository;

    private final ExerciseDeletionService exerciseDeletionService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    @Nullable
    private final ChatClient chatClient;

    public ExerciseVariantGenerationPipelineService(VariantTypeRegistryService typeRegistry, VariantAgentLoopService agentLoopRunner, ExerciseVariantJobService jobService,
            HyperionPromptTemplateService templateService, ExerciseRepository exerciseRepository, ExerciseDeletionService exerciseDeletionService,
            LLMTokenUsageService llmTokenUsageService, UserRepository userRepository, @Nullable ChatClient chatClient) {
        this.typeRegistry = typeRegistry;
        this.agentLoopRunner = agentLoopRunner;
        this.jobService = jobService;
        this.templateService = templateService;
        this.exerciseRepository = exerciseRepository;
        this.exerciseDeletionService = exerciseDeletionService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.chatClient = chatClient;
    }

    /**
     * Runs the whole pipeline for one job Called only from
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
            // Cannot be cancelled from here on — the last cancel window closed above. Guarded rather than a plain
            // updatePhase: a cancel accepted between that check and this transition would otherwise be answered
            // with success and then ignored (the guard and requestCancel share the job's lock).
            if (!jobService.enterFinalizingUnlessCancelled(jobId)) {
                throw new JobCancelledException();
            }
            List<String> warnings = new ArrayList<>();
            if (!report.passed()) {
                report.findings().forEach(finding -> warnings.add(finding.gate() + ": " + summarizeFindingForWarning(finding.message())));
            }
            try {
                warnings.addAll(adapters.finalizeVariant(variant, job));
            }
            catch (Exception e) {
                // From FINALIZING on, the generated variant is never discarded (the job is
                // not even cancellable anymore). A placement failure (e.g. the target group was deleted mid-job)
                // downgrades the result to a flagged draft instead of deleting verified LLM work.
                log.warn("Finalizing variant generation job {} failed; keeping the variant as a draft", jobId, e);
                warnings.add("FINALIZING: placement failed — assign the exercise to its group manually (" + e.getMessage() + ")");
            }
            // A flagged draft needs the same "what happened & how to continue" guidance a failure gets: the raw
            // gate messages are verification output, not instructions. Clean runs skip the extra LLM call.
            String instructorSummary = warnings.isEmpty() ? null : generateWarningSummary(job, warnings);
            jobService.complete(jobId, variant.getId(), warnings, instructorSummary);
            log.info("Variant generation job {} finished with {} for exercise {} -> variant {}", jobId, warnings.isEmpty() ? "COMPLETED" : "DRAFT_WITH_WARNINGS",
                    job.getSourceExerciseId(), variant.getId());
        }
        catch (JobCancelledException cancelled) {
            boolean cleanedUp = cleanupProvisionedVariant(variant, jobId);
            if (cleanedUp) {
                jobService.markCancelled(jobId);
            }
            else {
                // The clone survived the failed deletion, so the id is the only pointer to it — keep it and say so.
                jobService.markCancelledKeepingVariantExerciseId(jobId, cleanupFailedDetail(variantExerciseId(variant)));
            }
            log.info("Variant generation job {} cancelled (exercise {})", jobId, job.getSourceExerciseId());
        }
        catch (PhaseFailedException failure) {
            // Hard-failure policy: delete any half-created exercise, then FAILED. The
            // instructor summary is generated BEFORE the terminal transition so the FAILED event's detail
            // fetch already sees it (the modal loads the job detail as soon as the event arrives).
            Long leftover = leftoverExerciseId(failure);
            boolean cleanedUp = cleanupProvisionedVariant(variant, jobId) && leftover == null;
            if (leftover != null) {
                // A provisioner failed AND could not delete its own clone, so `variant` was never assigned:
                // record the id here, it is the only pointer the tray has to the surviving exercise.
                jobService.recordVariantExerciseId(jobId, leftover);
            }
            String instructorSummary = generateFailureSummary(job, failure.getMessage());
            if (cleanedUp) {
                jobService.fail(jobId, failure.getMessage(), instructorSummary);
            }
            else {
                jobService.failKeepingVariantExerciseId(jobId, failure.getMessage() + " " + cleanupFailedDetail(leftover != null ? leftover : variantExerciseId(variant)),
                        instructorSummary);
            }
            log.warn("Variant generation job {} failed: {}", jobId, failure.getMessage(), failure.getCause());
        }
        catch (Exception unexpected) {
            // Not every statement between PROVISIONING and the terminal transition sits inside runPhase: the
            // jobService bookkeeping calls (recordVariantExerciseId, recordStepOutput, updatePhase, complete) do
            // not, so a job-store failure escapes both typed catches. Without this block it would reach
            // ExerciseVariantTaskService, which marks the job FAILED but never deletes the exercise — leaving the
            // clone, its repositories and its build plans behind as orphans. No failure summary here: that is an
            // extra LLM call on a path we already know is broken.
            if (jobService.getJob(jobId, job.getInitiatorLogin()).map(current -> current.getPhase().isTerminal()).orElse(true)) {
                // The job already reached a terminal phase, so the throw came from AFTER that transition
                // (telemetry logging, the websocket publish). The variant is generated and the record says so —
                // deleting it here would discard verified work over a failed notification.
                log.error("Variant generation job {} raised an error after it had already finished (exercise {}); keeping the variant", jobId, job.getSourceExerciseId(),
                        unexpected);
                return;
            }
            String detail = "Unexpected error: " + unexpected.getMessage();
            Long leftover = leftoverExerciseId(unexpected);
            if (leftover != null) {
                jobService.recordVariantExerciseId(jobId, leftover);
            }
            if (cleanupProvisionedVariant(variant, jobId) && leftover == null) {
                jobService.fail(jobId, detail, null);
            }
            else {
                jobService.failKeepingVariantExerciseId(jobId, detail + " " + cleanupFailedDetail(leftover != null ? leftover : variantExerciseId(variant)), null);
            }
            log.error("Variant generation job {} failed unexpectedly (exercise {})", jobId, job.getSourceExerciseId(), unexpected);
        }
    }

    /**
     * The bounded transform → verify → repair loop. Returns the last
     * verification report; a non-passing report after budget exhaustion leads to DRAFT_WITH_WARNINGS —
     * the variant is kept as a flagged draft, never silently deleted.
     */
    private VerificationReport transformAndVerify(VariantJob job, VariantTypeAdapters adapters, Exercise variant, ChangePlan plan) {
        String jobId = job.getJobId();
        VariantAgentLoopService.AgentBudgets budgets = new VariantAgentLoopService.AgentBudgets(MAX_VERIFY_ATTEMPTS, TOKEN_BUDGET);
        String transformTemplate = transformPromptTemplate(job);
        VerificationReport report = null;
        // Stuck-repair-loop detection (see computeEscalationNote): the most recent rounds' finding signatures, a
        // deterministic backstop for when the prompt's own "search again instead of repeating" text doesn't land.
        Deque<Set<String>> recentSignatures = new ArrayDeque<>(STUCK_LOOP_WINDOW);
        String escalationNote = null;

        long tokensUsed = 0;
        for (int attempt = 1; attempt <= MAX_VERIFY_ATTEMPTS; attempt++) {
            checkCancelled(jobId);
            VariantJobPhase agentPhase = attempt == 1 ? VariantJobPhase.TRANSFORMING : VariantJobPhase.REPAIRING;
            jobService.updatePhase(jobId, agentPhase);
            jobService.recordAttempt(jobId, attempt, MAX_VERIFY_ATTEMPTS, attempt == 1 ? "Applying the change plan" : "Repairing verification findings");

            VariantToolset toolset = adapters.createTools(variant, job);
            // Repair rounds receive the previous round's findings (and, if stuck, the escalation note) as the
            // closed-loop repair signal.
            VerificationReport repairFeedback = report;
            String repairEscalationNote = escalationNote;
            VariantAgentLoopService.AgentResult agentResult = runPhase(agentPhase,
                    () -> agentLoopRunner.runLoop(plan, toolset, budgets, job, repairFeedback, repairEscalationNote, transformTemplate));
            tokensUsed += agentResult.tokensUsed();
            jobService.addTokensUsed(jobId, agentResult.tokensUsed());
            jobService.recordToolCallStats(jobId, toolset.toolCallStats());
            String roundSummary = "Agent round " + attempt + "/" + MAX_VERIFY_ATTEMPTS + " finished" + (agentResult.touchedTestRepo() ? " (test repository changed)" : "");
            jobService.recordStepOutput(jobId, agentPhase,
                    new StepOutput(roundSummary, agentResult.finishSummary() != null ? truncate(agentResult.finishSummary()) : "(no summary)", Instant.now()));

            checkCancelled(jobId);
            jobService.updatePhase(jobId, VariantJobPhase.VERIFYING);
            report = runPhase(VariantJobPhase.VERIFYING, () -> adapters.verify(variant, plan, job, toolset));
            jobService.recordStepOutput(jobId, VariantJobPhase.VERIFYING,
                    new StepOutput(report.passed() ? "All gates green" : report.findings().size() + " finding(s) — attempt " + attempt + "/" + MAX_VERIFY_ATTEMPTS,
                            renderReport(report), Instant.now()));
            // VERIFYING is the longest phase of a round (the programming gates wait for real CI builds), so a
            // cancel accepted while it ran must be honored here. Every exit below returns straight into
            // FINALIZING, which is past the last cancel window — without this check the job would finish anyway.
            checkCancelled(jobId);

            if (report.passed()) {
                return report;
            }
            escalationNote = computeEscalationNote(report, recentSignatures);
            // Token budget for the TRANSFORMING/REPAIRING sequence: when exhausted with
            // red gates, stop repairing — the job ends as DRAFT_WITH_WARNINGS with the budget noted.
            if (tokensUsed > TOKEN_BUDGET && attempt < MAX_VERIFY_ATTEMPTS) {
                log.info("Variant generation job {} exhausted the token budget ({} > {}) after attempt {}/{}", jobId, tokensUsed, TOKEN_BUDGET, attempt, MAX_VERIFY_ATTEMPTS);
                List<VerificationReport.VerificationFinding> findings = new ArrayList<>(report.findings());
                findings.add(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.TOKEN_BUDGET,
                        "The token budget was exhausted before all findings could be repaired (" + tokensUsed + " tokens used)."));
                return new VerificationReport(false, List.copyOf(findings));
            }
        }
        return report;
    }

    /**
     * Deterministic stuck-repair-loop detection: if the current round's failing findings share a signature (see
     * {@link VerificationReport#findingSignatures()}) with a round already recorded in the sliding window, the
     * prompt's own "search again instead of repeating" text has not worked for this problem — the escalation
     * note it returns gets injected into the NEXT repair round's prompt instead of relying on that text alone.
     * Checking the whole window (not just the immediately preceding round) also catches an oscillating A/B/A/B
     * stuck pattern, not just strict back-to-back repeats.
     *
     * @param report           this round's (failing) VerificationReport
     * @param recentSignatures the sliding window of previous rounds' finding-signature sets, mutated in place —
     *                             capped at {@link #STUCK_LOOP_WINDOW}, oldest dropped first
     * @return the escalation note for the next round's repair prompt, or {@code null} when nothing looks stuck
     */
    @Nullable
    private String computeEscalationNote(VerificationReport report, Deque<Set<String>> recentSignatures) {
        Set<String> currentSignatures = report.findingSignatures();
        long occurrences = 1 + recentSignatures.stream().filter(previous -> !Collections.disjoint(previous, currentSignatures)).count();
        if (recentSignatures.size() == STUCK_LOOP_WINDOW) {
            recentSignatures.removeFirst();
        }
        recentSignatures.addLast(currentSignatures);
        if (occurrences < STUCK_LOOP_REPEAT_THRESHOLD) {
            return null;
        }
        return "This exact problem was already reported unresolved after your last fix — whatever you tried didn't work, do not repeat the same kind of edit. "
                + "Check whether the thing causing this is even listed in the plan's intendedChanges above; if it isn't, you introduced it yourself in an earlier round, "
                + "and removing it (deleteFiles/applyEdit) is more likely to fix this than patching it further.";
    }

    /**
     * PLANNING: one structured LLM call producing the {@link ChangePlan}, with the
     * malformed-output policy: the conversion error is fed back to the model, at most
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
                ChatResponse chatResponse = chatClient.prompt().system(systemPrompt).user(userMessage + repromptSuffix).call().chatResponse();
                trackTokenUsage(job, chatResponse, PLAN_PIPELINE_ID);
                ChangePlan plan = outputConverter.convert(LLMTokenUsageService.extractResponseText(chatResponse));
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

    /**
     * One best-effort LLM call producing the instructor-facing failure summary: what
     * state the exercise landed in (source untouched, clone deleted) and how to fix or retry. Grounded in
     * the recorded step outputs and the change plan. Never fails the failure handling — returns null when
     * the chat client is missing or the call itself errors.
     */
    private String generateFailureSummary(VariantJob job, String failureMessage) {
        Map<String, String> variables = new HashMap<>();
        // The local job copy never sees the phase updates (they mutate the map record) — read the live phase.
        variables.put("failedPhase", String.valueOf(jobService.getJob(job.getJobId(), job.getInitiatorLogin()).orElse(job).getPhase()));
        variables.put("failureDetail", orDefault(failureMessage, "(no detail)"));
        return generateInstructorSummary(job, "prompts/hyperion/variants/failure_summary.st", FAILURE_SUMMARY_PIPELINE_ID, variables);
    }

    /**
     * The DRAFT_WITH_WARNINGS counterpart of {@link #generateFailureSummary}: the variant exists but did not pass
     * every gate, so the instructor needs to know what is broken and what to fix before publishing.
     *
     * @param job      the job that produced the flagged draft
     * @param warnings the recorded gate warnings, never empty
     * @return the summary, or null when it could not be produced
     */
    private String generateWarningSummary(VariantJob job, List<String> warnings) {
        Map<String, String> variables = new HashMap<>();
        variables.put("warnings", String.join("\n", warnings));
        return generateInstructorSummary(job, "prompts/hyperion/variants/warning_summary.st", WARNING_SUMMARY_PIPELINE_ID, variables);
    }

    /**
     * One best-effort LLM call producing an instructor-facing post-mortem, grounded in the recorded step outputs,
     * the change plan and the original request. Never fails the terminal handling — returns null when the chat
     * client is missing or the call itself errors.
     *
     * @param job            the job the summary is written for
     * @param templatePath   the prompt template describing the outcome (failure / flagged draft)
     * @param pipelineId     the token-usage trace id of this outcome's summary call
     * @param outcomeContext outcome-specific template variables, merged into the shared job context
     * @return the summary, or null when it could not be produced
     */
    private String generateInstructorSummary(VariantJob job, String templatePath, String pipelineId, Map<String, String> outcomeContext) {
        if (chatClient == null) {
            return null;
        }
        try {
            // The local job copy is stale (step outputs are recorded via the job service) — read the map record.
            VariantJob currentJob = jobService.getJob(job.getJobId(), job.getInitiatorLogin()).orElse(job);
            Map<String, String> variables = new HashMap<>(outcomeContext);
            variables.put("exerciseType", String.valueOf(job.getExerciseType()));
            variables.put("sourceTitle", orDefault(job.getSourceExerciseTitle(), "(unknown)"));
            variables.put("targetDifficulty", job.getRequest().targetDifficulty() != null ? job.getRequest().targetDifficulty().name() : "unchanged");
            variables.put("domainText", orDefault(job.getRequest().domainText(), "unchanged"));
            variables.put("narrativeStyle", job.getRequest().narrativeStyle() != null ? job.getRequest().narrativeStyle().name() : "consistent with the source");
            variables.put("additionalInstructions", orDefault(job.getRequest().additionalInstructions(), "none"));
            variables.put("changePlan", currentJob.getChangePlan() != null ? renderPlan(currentJob.getChangePlan()) : "No change plan was produced yet.");
            variables.put("stepOutputs", renderStepOutputs(currentJob));
            String systemPrompt = templateService.render(templatePath, variables);
            ChatResponse chatResponse = chatClient.prompt().system(systemPrompt).user("Write the summary for the instructor now.").call().chatResponse();
            trackTokenUsage(job, chatResponse, pipelineId);
            String summary = LLMTokenUsageService.extractResponseText(chatResponse);
            return summary == null || summary.isBlank() ? null : truncate(summary.strip());
        }
        catch (Exception e) {
            log.warn("Instructor-summary generation for variant job {} failed itself — continuing without a summary", job.getJobId(), e);
            return null;
        }
    }

    private String renderStepOutputs(VariantJob job) {
        if (job.getStepOutputs() == null || job.getStepOutputs().isEmpty()) {
            return "No steps were completed.";
        }
        StringBuilder builder = new StringBuilder();
        for (VariantJobPhase phase : VariantJobPhase.values()) {
            List<StepOutput> outputs = job.getStepOutputs().get(phase);
            if (outputs != null) {
                // Every recorded message, oldest first — earlier attempt failures are exactly what the
                // failure summary needs to explain.
                outputs.forEach(output -> builder.append(phase).append(": ").append(output.summary()).append('\n'));
            }
        }
        return builder.toString();
    }

    private void trackTokenUsage(VariantJob job, ChatResponse chatResponse, String pipelineId) {
        Long userId = userRepository.findOneByLogin(job.getInitiatorLogin()).map(User::getId).orElse(null);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, pipelineId,
                builder -> builder.withExercise(job.getSourceExerciseId()).withUser(userId));
        if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null
                && chatResponse.getMetadata().getUsage().getTotalTokens() != null) {
            jobService.addTokensUsed(job.getJobId(), chatResponse.getMetadata().getUsage().getTotalTokens());
        }
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
        if (plan.invariants() == null) {
            // Rendering the plan dereferences this list outside the planning retry loop, so an absent JSON property
            // (which the converter leaves null) would surface as an unusable "Unexpected error: null" instead of the
            // re-prompt this validation exists for.
            throw new IllegalArgumentException("invariants must not be missing (use an empty list when there are none)");
        }
    }

    private Map<String, String> promptVariables(VariantJob job, String sourceContext) {
        Map<String, String> variables = new HashMap<>();
        variables.put("sourceContext", sourceContext);
        variables.put("targetDifficulty", job.getRequest().targetDifficulty() != null ? job.getRequest().targetDifficulty().name() : "unchanged");
        variables.put("domainText", orDefault(job.getRequest().domainText(), "unchanged"));
        variables.put("narrativeStyle", job.getRequest().narrativeStyle() != null ? job.getRequest().narrativeStyle().name() : "consistent with the source");
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
     * between tool rounds (never mid-LLM-call or mid-build).
     */
    private void checkCancelled(String jobId) {
        if (jobService.isCancelRequested(jobId)) {
            throw new JobCancelledException();
        }
    }

    /**
     * Deletes a provisioned half-exercise via the existing deletion service — repos and build plans are
     * cleaned up on the same path as regular exercise deletion (hard-failure and cancel paths).
     *
     * @return true when nothing is left behind (deleted, or never provisioned); false when the clone survived a
     *         failed deletion — the caller must then KEEP the variant exercise id, which is the only pointer to it
     */
    private boolean cleanupProvisionedVariant(@Nullable Exercise variant, String jobId) {
        if (variant == null || variant.getId() == null) {
            return true;
        }
        try {
            exerciseDeletionService.delete(variant.getId(), true);
            return true;
        }
        catch (Exception e) {
            // Never mask the original failure/cancellation with a cleanup error — flag it for manual cleanup.
            log.error("Failed to clean up provisioned variant exercise {} for job {}", variant.getId(), jobId, e);
            return false;
        }
    }

    /** Instructor-visible note appended to the terminal detail when the clone could not be deleted. */
    private static String cleanupFailedDetail(@Nullable Long exerciseId) {
        return "The generated exercise" + (exerciseId != null ? " (id " + exerciseId + ")" : "") + " could not be deleted automatically — delete it manually.";
    }

    @Nullable
    private static Long variantExerciseId(@Nullable Exercise variant) {
        return variant == null ? null : variant.getId();
    }

    /**
     * Walks the cause chain for a {@link LeftoverVariantExerciseException} — a provisioner failed and could not
     * delete the exercise its import had already persisted. The pipeline's own {@code variant} is still null on
     * that path (provision never returned), so this id is the only record of the surviving clone.
     *
     * @param throwable the exception that ended the job
     * @return the surviving exercise id, or null when nothing was left behind
     */
    @Nullable
    static Long leftoverExerciseId(@Nullable Throwable throwable) {
        // Depth-bounded rather than cycle-checked: a cause chain can be circular, and the wrapping here is at
        // most provisioner -> RuntimeException -> PhaseFailedException.
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_CHAIN_DEPTH; depth++) {
            if (current instanceof LeftoverVariantExerciseException leftover) {
                return leftover.getExerciseId();
            }
            current = current.getCause();
        }
        return null;
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
        return truncate(report.toAgentFeedback());
    }

    /**
     * Warnings render as a flat list in the modal's result step, but a build-gate finding message carries
     * the full build logs (the agent's repair signal, up to ~10k chars). Cut the message at the build-log
     * section and cap the rest — the complete finding stays inspectable in the VERIFYING step output.
     */
    private static String summarizeFindingForWarning(String message) {
        if (message == null) {
            return "";
        }
        int buildLogsIndex = message.indexOf(VariantBuildVerificationService.BUILD_LOGS_SECTION);
        String summary = buildLogsIndex >= 0 ? message.substring(0, buildLogsIndex) + "\n(build logs omitted — see the verification step log)" : message;
        final int maxLength = 1500;
        return summary.length() <= maxLength ? summary : summary.substring(0, maxLength) + " […]";
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
