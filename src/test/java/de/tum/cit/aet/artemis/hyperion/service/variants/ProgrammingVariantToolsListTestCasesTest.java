package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/**
 * Unit tests for the {@code listTestCases} tool: gives the agent the exact, current test names before it writes or
 * updates problem-statement task markers, so referenced names are exact rather than remembered/guessed.
 */
class ProgrammingVariantToolsListTestCasesTest {

    private static final String JOB_ID = "job-1";

    private ProgrammingVariantTools tools(Set<ProgrammingExerciseTestCase> testCases) {
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);

        ProgrammingExerciseTestCaseRepository testCaseRepository = mock(ProgrammingExerciseTestCaseRepository.class);
        when(testCaseRepository.findByExerciseId(anyLong())).thenReturn(testCases);

        ExerciseVariantJobService jobService = mock(ExerciseVariantJobService.class);
        when(jobService.isCancelRequested(JOB_ID)).thenReturn(false);

        return new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, null, null, null, null, null, null, null, null, "main", null, testCaseRepository);
    }

    @Test
    void shouldReturnSortedTestNames() {
        ProgrammingVariantTools tools = tools(Set.of(new ProgrammingExerciseTestCase().testName("testMergeSort"), new ProgrammingExerciseTestCase().testName("testBubbleSort")));

        assertThat(tools.listTestCases()).isEqualTo("testBubbleSort\ntestMergeSort");
    }

    @Test
    void shouldExplainWhenNoTestCasesAreRegisteredYet() {
        ProgrammingVariantTools tools = tools(Set.of());

        assertThat(tools.listTestCases()).contains("No test cases are registered yet").contains("run a build first");
    }
}
