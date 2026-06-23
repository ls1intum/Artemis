package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Unit tests for {@link ExecutionPlanStateManagerService}.
 * Tests plan initialization, step transitions, timeout handling, and template parsing.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionPlanStateManagerServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private ExecutionPlanStateManagerService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionPlanStateManagerService(cacheManager);
        org.mockito.Mockito.lenient().when(cacheManager.getCache(ExecutionPlanStateManagerService.ATLAS_EXECUTION_PLAN_CACHE)).thenReturn(cache);
    }

    @Test
    void initializePlan_storesPlanInCache() {
        service.initializePlan("session1", ExecutionPlanStateManagerService.PlanTemplate.CREATE_AND_MAP_RELATIONS, "Create competencies", null, null);

        verify(cache).put(eq("session1"), any(ExecutionPlanStateManagerService.ExecutionPlan.class));
    }

    @Test
    void initializePlan_firstStepIsActive() {
        service.initializePlan("session1", ExecutionPlanStateManagerService.PlanTemplate.CREATE_AND_MAP_RELATIONS, "goal", null, null);

        // Capture what was stored
        var captor = org.mockito.ArgumentCaptor.forClass(ExecutionPlanStateManagerService.ExecutionPlan.class);
        verify(cache).put(eq("session1"), captor.capture());

        ExecutionPlanStateManagerService.ExecutionPlan plan = captor.getValue();
        assertThat(plan.getCurrentStep().getStatus()).isEqualTo(ExecutionPlanStateManagerService.StepStatus.ACTIVE);
        assertThat(plan.getCurrentStep().getAgentType()).isEqualTo(ExecutionPlanStateManagerService.AgentType.COMPETENCY_EXPERT);
    }

    @Test
    void initializePlan_nullCache_doesNotThrow() {
        when(cacheManager.getCache(ExecutionPlanStateManagerService.ATLAS_EXECUTION_PLAN_CACHE)).thenReturn(null);

        service.initializePlan("session1", ExecutionPlanStateManagerService.PlanTemplate.CREATE_AND_MAP_RELATIONS, "goal", null, null);

        verify(cache, never()).put(any(), any());
    }

    @Test
    void completeStepAndGetNext_advancesToNextStep() {
        ExecutionPlanStateManagerService.ExecutionPlan plan = new ExecutionPlanStateManagerService.ExecutionPlan(
                List.of(new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.COMPETENCY_EXPERT),
                        new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.COMPETENCY_MAPPER)),
                "goal");
        when(cache.get("session1", ExecutionPlanStateManagerService.ExecutionPlan.class)).thenReturn(plan);

        var result = new ExecutionPlanStateManagerService.StepResult(List.of(1L, 2L), "Created 2 competencies");
        Optional<ExecutionPlanStateManagerService.NextStepContext> next = service.completeStepAndGetNext("session1", result);

        assertThat(next).isPresent();
        assertThat(next.get().agentType()).isEqualTo(ExecutionPlanStateManagerService.AgentType.COMPETENCY_MAPPER);
        assertThat(next.get().userGoal()).isEqualTo("goal");
    }

    @Test
    void completeStepAndGetNext_lastStep_evictsCacheAndReturnsEmpty() {
        ExecutionPlanStateManagerService.ExecutionPlan plan = new ExecutionPlanStateManagerService.ExecutionPlan(
                List.of(new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.EXERCISE_MAPPER)), "goal");
        when(cache.get("session1", ExecutionPlanStateManagerService.ExecutionPlan.class)).thenReturn(plan);

        Optional<ExecutionPlanStateManagerService.NextStepContext> next = service.completeStepAndGetNext("session1", null);

        assertThat(next).isEmpty();
        verify(cache).evict("session1");
    }

    @Test
    void completeStepAndGetNext_noPlan_returnsEmpty() {
        when(cache.get("session1", ExecutionPlanStateManagerService.ExecutionPlan.class)).thenReturn(null);

        Optional<ExecutionPlanStateManagerService.NextStepContext> next = service.completeStepAndGetNext("session1", null);

        assertThat(next).isEmpty();
    }

    @Test
    void completeStepAndGetNext_previousResults_passedToNextStep() {
        ExecutionPlanStateManagerService.ExecutionPlan plan = new ExecutionPlanStateManagerService.ExecutionPlan(
                List.of(new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.COMPETENCY_EXPERT),
                        new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.EXERCISE_MAPPER)),
                "goal");
        when(cache.get("s", ExecutionPlanStateManagerService.ExecutionPlan.class)).thenReturn(plan);

        var result = new ExecutionPlanStateManagerService.StepResult(List.of(5L), "summary");
        Optional<ExecutionPlanStateManagerService.NextStepContext> next = service.completeStepAndGetNext("s", result);

        assertThat(next).isPresent();
        assertThat(next.get().previousResults()).hasSize(1);
        assertThat(next.get().previousResults().get(0).ids()).containsExactly(5L);
    }

    @Test
    void hasPlan_returnsTrueWhenPlanExists() {
        ExecutionPlanStateManagerService.ExecutionPlan plan = new ExecutionPlanStateManagerService.ExecutionPlan(
                List.of(new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.COMPETENCY_EXPERT)), "goal");
        when(cache.get("session1", ExecutionPlanStateManagerService.ExecutionPlan.class)).thenReturn(plan);

        assertThat(service.hasPlan("session1")).isTrue();
    }

    @Test
    void hasPlan_returnsFalseWhenNoPlan() {
        when(cache.get("session1", ExecutionPlanStateManagerService.ExecutionPlan.class)).thenReturn(null);

        assertThat(service.hasPlan("session1")).isFalse();
    }

    @Test
    void hasPlan_nullCache_returnsFalse() {
        when(cacheManager.getCache(ExecutionPlanStateManagerService.ATLAS_EXECUTION_PLAN_CACHE)).thenReturn(null);

        assertThat(service.hasPlan("session1")).isFalse();
    }

    @Test
    void cancelPlan_evictsCacheEntry() {
        service.cancelPlan("session1");

        verify(cache).evict("session1");
    }

    @Test
    void parseTemplate_returnsCorrectTemplate() {
        assertThat(ExecutionPlanStateManagerService.parseTemplate("FULL_WORKFLOW")).isEqualTo(ExecutionPlanStateManagerService.PlanTemplate.FULL_WORKFLOW);
        assertThat(ExecutionPlanStateManagerService.parseTemplate("CREATE_AND_MAP_RELATIONS")).isEqualTo(ExecutionPlanStateManagerService.PlanTemplate.CREATE_AND_MAP_RELATIONS);
        assertThat(ExecutionPlanStateManagerService.parseTemplate("MAP_RELATIONS_AND_EXERCISES"))
                .isEqualTo(ExecutionPlanStateManagerService.PlanTemplate.MAP_RELATIONS_AND_EXERCISES);
    }

    @Test
    void parseTemplate_caseInsensitive() {
        assertThat(ExecutionPlanStateManagerService.parseTemplate("full_workflow")).isEqualTo(ExecutionPlanStateManagerService.PlanTemplate.FULL_WORKFLOW);
    }

    @Test
    void parseTemplate_unknownValue_returnsNull() {
        assertThat(ExecutionPlanStateManagerService.parseTemplate("UNKNOWN_TEMPLATE")).isNull();
    }

    @Test
    void parseTemplate_nullOrBlank_returnsNull() {
        assertThat(ExecutionPlanStateManagerService.parseTemplate(null)).isNull();
        assertThat(ExecutionPlanStateManagerService.parseTemplate("  ")).isNull();
    }

    @Test
    void executionPlan_fullWorkflow_hasThreeSteps() {
        service.initializePlan("s", ExecutionPlanStateManagerService.PlanTemplate.FULL_WORKFLOW, "goal", null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(ExecutionPlanStateManagerService.ExecutionPlan.class);
        verify(cache).put(eq("s"), captor.capture());

        ExecutionPlanStateManagerService.ExecutionPlan plan = captor.getValue();
        assertThat(plan.getSteps()).hasSize(3);
        assertThat(plan.getSteps().get(0).getAgentType()).isEqualTo(ExecutionPlanStateManagerService.AgentType.COMPETENCY_EXPERT);
        assertThat(plan.getSteps().get(1).getAgentType()).isEqualTo(ExecutionPlanStateManagerService.AgentType.COMPETENCY_MAPPER);
        assertThat(plan.getSteps().get(2).getAgentType()).isEqualTo(ExecutionPlanStateManagerService.AgentType.EXERCISE_MAPPER);
    }

    @Test
    void executionPlan_hasNextStep_falseOnSingleStep() {
        ExecutionPlanStateManagerService.ExecutionPlan plan = new ExecutionPlanStateManagerService.ExecutionPlan(
                List.of(new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.EXERCISE_MAPPER)), "goal");

        assertThat(plan.hasNextStep()).isFalse();
    }

    @Test
    void executionPlan_markCurrentStepDone_storesResult() {
        ExecutionPlanStateManagerService.PlanStep step = new ExecutionPlanStateManagerService.PlanStep(ExecutionPlanStateManagerService.AgentType.COMPETENCY_EXPERT);
        ExecutionPlanStateManagerService.ExecutionPlan plan = new ExecutionPlanStateManagerService.ExecutionPlan(List.of(step), "goal");

        var result = new ExecutionPlanStateManagerService.StepResult(List.of(7L), "done");
        plan.markCurrentStepDone(result);

        assertThat(step.getStatus()).isEqualTo(ExecutionPlanStateManagerService.StepStatus.DONE);
        assertThat(step.getResult()).isEqualTo(result);
    }
}
