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

        return new ProgrammingVariantTools(exercise, null, JOB_ID, jobService, null, null, null, null, "main", null, testCaseRepository, (exerciseArgument, jobArgument) -> {
        });
    }

    @Test
    void shouldReturnSortedTestNames() {
        ProgrammingVariantTools tools = tools(Set.of(new ProgrammingExerciseTestCase().testName("testMergeSort"), new ProgrammingExerciseTestCase().testName("testBubbleSort")));

        // The names must end the output, one per line and sorted, so they stay copyable verbatim; the preamble
        // in front of them is guidance and is asserted separately rather than pinned word for word.
        assertThat(tools.listTestCases()).endsWith("testBubbleSort\ntestMergeSort");
    }

    @Test
    void shouldStateThatTheListedNamesAreTheCompleteSet() {
        ProgrammingVariantTools tools = tools(Set.of(new ProgrammingExerciseTestCase().testName("testBubbleSort")));

        // Agents were observed rewriting generated names such as testClass[SortStrategy] into tidier-looking ones,
        // which silently unlinks the task from grading — the output has to read as a closed set, not a suggestion.
        assertThat(tools.listTestCases()).contains("complete set").contains("character for character");
    }

    @Test
    void shouldExplainWhenNoTestCasesAreRegisteredYet() {
        ProgrammingVariantTools tools = tools(Set.of());

        assertThat(tools.listTestCases()).contains("No test cases are registered yet").contains("run a build first");
    }
}
