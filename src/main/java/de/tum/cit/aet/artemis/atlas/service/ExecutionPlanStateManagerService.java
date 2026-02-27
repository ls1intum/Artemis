package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * State manager for multi-step execution plans in the Atlas Agent system.
 * Coordinates the execution of plans that span multiple sub-agents (Competency Expert,
 * Competency Mapper, Exercise Mapper) in a predefined sequence.
 *
 * This service is the single source of truth for plan state. Sub-agents remain unchanged -
 * AtlasAgentService checks this manager after each approval to determine if there's a next step.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class ExecutionPlanStateManagerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPlanStateManagerService.class);

    /**
     * Cache name for storing execution plans.
     * Configured in CacheConfiguration with appropriate TTL.
     */
    public static final String ATLAS_EXECUTION_PLAN_CACHE = "atlas-execution-plan";

    /**
     * Maximum duration a plan can remain active before automatic expiration.
     */
    private static final Duration PLAN_TIMEOUT = Duration.ofMinutes(30);

    /**
     * Maximum number of steps allowed in a plan to prevent infinite loops.
     */
    private static final int MAX_STEPS = 5;

    private final CacheManager cacheManager;

    public ExecutionPlanStateManagerService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Predefined plan templates covering common multi-agent workflows.
     * The orchestrator selects a template rather than generating dynamic plans,
     * ensuring reliable parsing and execution.
     */
    public enum PlanTemplate {
        /**
         * Create competencies, then map relations between them.
         * Agents: COMPETENCY_EXPERT -> COMPETENCY_MAPPER
         */
        CREATE_AND_MAP_RELATIONS,

        /**
         * Create competencies, then link them to exercises.
         * Agents: COMPETENCY_EXPERT -> EXERCISE_MAPPER
         */
        CREATE_AND_MAP_EXERCISES,

        /**
         * Full workflow: create competencies, map relations, then link to exercises.
         * Agents: COMPETENCY_EXPERT -> COMPETENCY_MAPPER -> EXERCISE_MAPPER
         */
        FULL_WORKFLOW,

        /**
         * Map relations between existing competencies, then link to exercises.
         * Agents: COMPETENCY_MAPPER -> EXERCISE_MAPPER
         */
        MAP_RELATIONS_AND_EXERCISES
    }

    /**
     * Agent types that can participate in execution plans.
     */
    public enum AgentType {
        COMPETENCY_EXPERT, COMPETENCY_MAPPER, EXERCISE_MAPPER
    }

    /**
     * Status of a plan step.
     */
    public enum StepStatus {
        PENDING, ACTIVE, DONE
    }

    /**
     * Result of completing a step, containing IDs and a human-readable summary.
     * Used to pass context to subsequent steps.
     */
    public record StepResult(List<Long> ids, String summary) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * Context for the next step in the plan, including accumulated results from previous steps.
     */
    public record NextStepContext(AgentType agentType, String userGoal, List<StepResult> previousResults) {
    }

    /**
     * A single step in an execution plan.
     */
    public static class PlanStep implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final AgentType agentType;

        private StepStatus status;

        @Nullable
        private StepResult result;

        public PlanStep(AgentType agentType) {
            this.agentType = agentType;
            this.status = StepStatus.PENDING;
            this.result = null;
        }

        public AgentType getAgentType() {
            return agentType;
        }

        public StepStatus getStatus() {
            return status;
        }

        public void setStatus(StepStatus status) {
            this.status = status;
        }

        @Nullable
        public StepResult getResult() {
            return result;
        }

        public void setResult(@Nullable StepResult result) {
            this.result = result;
        }
    }

    /**
     * An execution plan containing multiple steps to be executed in sequence.
     */
    public static class ExecutionPlan implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final List<PlanStep> steps;

        private int currentStepIndex;

        private final String userGoal;

        private final Instant createdAt;

        public ExecutionPlan(List<PlanStep> steps, String userGoal) {
            this.steps = new ArrayList<>(steps);
            this.currentStepIndex = 0;
            this.userGoal = userGoal;
            this.createdAt = Instant.now();

            // Mark the first step as active
            if (!steps.isEmpty()) {
                this.steps.get(0).setStatus(StepStatus.ACTIVE);
            }
        }

        public List<PlanStep> getSteps() {
            return steps;
        }

        public String getUserGoal() {
            return userGoal;
        }

        public boolean hasNextStep() {
            return currentStepIndex < steps.size() - 1;
        }

        public PlanStep getCurrentStep() {
            if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
                return steps.get(currentStepIndex);
            }
            return null;
        }

        public void markCurrentStepDone(@Nullable StepResult result) {
            PlanStep current = getCurrentStep();
            if (current != null) {
                current.setStatus(StepStatus.DONE);
                current.setResult(result);
            }
        }

        public void advanceToNextStep() {
            if (hasNextStep()) {
                currentStepIndex++;
                steps.get(currentStepIndex).setStatus(StepStatus.ACTIVE);
            }
        }

        public List<StepResult> getAllPreviousResults() {
            List<StepResult> results = new ArrayList<>();
            for (int i = 0; i < currentStepIndex; i++) {
                StepResult result = steps.get(i).getResult();
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
        }

        public boolean isExpired() {
            return createdAt.plus(PLAN_TIMEOUT).isBefore(Instant.now());
        }
    }

    /**
     * Initializes a new execution plan for a session based on a predefined template.
     *
     * @param sessionId the session ID
     * @param template  the plan template to use
     * @param userGoal  the original user request (for context in subsequent steps)
     */
    public void initializePlan(String sessionId, PlanTemplate template, String userGoal) {
        List<AgentType> agentSequence = getAgentSequence(template);

        if (agentSequence.size() > MAX_STEPS) {
            log.warn("Plan template {} has {} steps, exceeding max of {}. Truncating.", template, agentSequence.size(), MAX_STEPS);
            agentSequence = agentSequence.subList(0, MAX_STEPS);
        }

        List<PlanStep> steps = agentSequence.stream().map(PlanStep::new).toList();

        ExecutionPlan plan = new ExecutionPlan(steps, userGoal);

        Cache cache = cacheManager.getCache(ATLAS_EXECUTION_PLAN_CACHE);
        if (cache != null) {
            cache.put(sessionId, plan);
            log.info("Initialized execution plan for session {}: template={}, steps={}", sessionId, template, agentSequence);
        }
    }

    /**
     * Completes the current step and returns context for the next step if one exists.
     * Called by AtlasAgentService after a successful approval/persistence.
     *
     * @param sessionId the session ID
     * @param result    the result of the completed step (IDs and summary)
     * @return context for the next step, or empty if plan is complete or doesn't exist
     */
    public Optional<NextStepContext> completeStepAndGetNext(String sessionId, @Nullable StepResult result) {
        Cache cache = cacheManager.getCache(ATLAS_EXECUTION_PLAN_CACHE);
        if (cache == null) {
            return Optional.empty();
        }

        ExecutionPlan plan = cache.get(sessionId, ExecutionPlan.class);
        if (plan == null) {
            return Optional.empty();
        }

        if (plan.isExpired()) {
            log.info("Plan for session {} has expired. Clearing.", sessionId);
            cache.evict(sessionId);
            return Optional.empty();
        }

        PlanStep currentStep = plan.getCurrentStep();
        if (currentStep == null || currentStep.getStatus() != StepStatus.ACTIVE) {
            log.warn("Ignoring duplicate or out-of-order completion for session {}. Step status: {}", sessionId, currentStep != null ? currentStep.getStatus() : "null");
            return Optional.empty();
        }

        plan.markCurrentStepDone(result);

        if (plan.hasNextStep()) {
            plan.advanceToNextStep();
            cache.put(sessionId, plan);

            PlanStep nextStep = plan.getCurrentStep();
            if (nextStep != null) {
                log.info("Advancing to next step for session {}: agent={}", sessionId, nextStep.getAgentType());
                return Optional.of(new NextStepContext(nextStep.getAgentType(), plan.getUserGoal(), plan.getAllPreviousResults()));
            }
        }

        log.info("Execution plan complete for session {}", sessionId);
        cache.evict(sessionId);
        return Optional.empty();
    }

    /**
     * Checks if a plan exists for the given session.
     *
     * @param sessionId the session ID
     * @return true if an active plan exists
     */
    public boolean hasPlan(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_EXECUTION_PLAN_CACHE);
        if (cache == null) {
            return false;
        }

        ExecutionPlan plan = cache.get(sessionId, ExecutionPlan.class);
        return plan != null && !plan.isExpired();
    }

    /**
     * Cancels and removes the execution plan for a session.
     * Called when user says "stop" or "cancel", or on error.
     *
     * @param sessionId the session ID
     */
    public void cancelPlan(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_EXECUTION_PLAN_CACHE);
        if (cache != null) {
            cache.evict(sessionId);
            log.info("Cancelled execution plan for session {}", sessionId);
        }
    }

    /**
     * Gets the agent sequence for a given plan template.
     *
     * @param template the plan template
     * @return ordered list of agent types to execute
     */
    private List<AgentType> getAgentSequence(PlanTemplate template) {
        return switch (template) {
            case CREATE_AND_MAP_RELATIONS -> List.of(AgentType.COMPETENCY_EXPERT, AgentType.COMPETENCY_MAPPER);
            case CREATE_AND_MAP_EXERCISES -> List.of(AgentType.COMPETENCY_EXPERT, AgentType.EXERCISE_MAPPER);
            case FULL_WORKFLOW -> List.of(AgentType.COMPETENCY_EXPERT, AgentType.COMPETENCY_MAPPER, AgentType.EXERCISE_MAPPER);
            case MAP_RELATIONS_AND_EXERCISES -> List.of(AgentType.COMPETENCY_MAPPER, AgentType.EXERCISE_MAPPER);
        };
    }

    /**
     * Gets the plan template from a marker string.
     * Used when parsing the orchestrator's output.
     *
     * @param marker the marker string (e.g., "FULL_WORKFLOW")
     * @return the corresponding PlanTemplate, or null if not found
     */
    @Nullable
    public static PlanTemplate parseTemplate(String marker) {
        if (marker == null || marker.isBlank()) {
            return null;
        }

        try {
            return PlanTemplate.valueOf(marker.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            log.warn("Unknown plan template: {}", marker);
            return null;
        }
    }
}
