package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/**
 * Unit tests for {@link ProgrammingExerciseTaskService#findUnresolvedTaskTestReferences}: unlike
 * {@code extractTasks}, which silently drops task-marker test references that don't resolve to a real test case,
 * this reports them so a caller can surface a precise error (e.g. a variant-generation verify gate) instead of a
 * silently-broken task-test link.
 */
class ProgrammingExerciseTaskServiceTest {

    private static final long EXERCISE_ID = 42L;

    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    private ProgrammingExerciseTaskService taskService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        ProgrammingExerciseTaskRepository taskRepository = mock(ProgrammingExerciseTaskRepository.class);
        testCaseRepository = mock(ProgrammingExerciseTestCaseRepository.class);
        taskService = new ProgrammingExerciseTaskService(taskRepository, testCaseRepository);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(EXERCISE_ID);
    }

    private void withTestCases(String... names) {
        Set<ProgrammingExerciseTestCase> testCases = java.util.Arrays.stream(names).map(name -> new ProgrammingExerciseTestCase().testName(name).id((long) name.hashCode()))
                .collect(java.util.stream.Collectors.toSet());
        when(testCaseRepository.findByExerciseIdAndActive(anyLong(), eq(true))).thenReturn(testCases);
    }

    @Test
    void shouldReturnEmptyWhenEveryReferenceResolves() {
        withTestCases("testBubbleSort", "testMergeSort");
        when(exercise.getProblemStatement()).thenReturn("[task][Sort](testBubbleSort,testMergeSort)");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).isEmpty();
    }

    @Test
    void shouldReportAStaleOrTypodTestName() {
        withTestCases("testBubbleSort");
        when(exercise.getProblemStatement()).thenReturn("[task][Sort](testBubbleSort,testMergeSortTypo)");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).containsExactly("testMergeSortTypo");
    }

    @Test
    void shouldReportEveryUnresolvedReferenceAcrossMultipleTasks() {
        withTestCases("testBubbleSort");
        when(exercise.getProblemStatement()).thenReturn("[task][Sort](testBubbleSort)\n[task][Merge](testMergeSortMissing)\n[task][Other](testOtherMissing)");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).containsExactly("testMergeSortMissing", "testOtherMissing");
    }

    @Test
    void shouldReturnEmptyForABlankProblemStatement() {
        when(exercise.getProblemStatement()).thenReturn("");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenThereAreNoTaskMarkers() {
        withTestCases("testBubbleSort");
        when(exercise.getProblemStatement()).thenReturn("Just prose, no task markers here.");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).isEmpty();
    }

    @Test
    void shouldResolveATestIdReferenceTheSameWayExtractTasksDoes() {
        ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase().testName("testBubbleSort").id(7L);
        when(testCaseRepository.findByExerciseIdAndActive(anyLong(), eq(true))).thenReturn(Set.of(testCase));
        when(exercise.getProblemStatement()).thenReturn("[task][Sort](<testid>7</testid>)");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).isEmpty();
    }

    /**
     * Regression: a generated variant is cloned from its source WITH the source's test cases, so after the agent
     * renames the tests, the source's rows survive as INACTIVE ones. A stale reference to such a name must be
     * reported — matching it would pass the variant-generation verify gate for a task that grading (active test
     * cases only) can never link, which is what produced a "successful" variant with every task unlinked.
     */
    @Test
    void shouldReportAReferenceThatOnlyResolvesToAnInactiveTestCase() {
        ProgrammingExerciseTestCase renamed = new ProgrammingExerciseTestCase().testName("testLettuceSort").id(2L);
        // The stale "testBubbleSort" row still exists on the exercise, but the current test repository no longer
        // produces it — findByExerciseIdAndActive(.., true) therefore does not return it.
        when(testCaseRepository.findByExerciseIdAndActive(anyLong(), eq(true))).thenReturn(Set.of(renamed));
        when(exercise.getProblemStatement()).thenReturn("[task][Sort](testBubbleSort,testLettuceSort)");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).containsExactly("testBubbleSort");
    }

    @Test
    void shouldReportAnUnresolvedTestIdReference() {
        when(testCaseRepository.findByExerciseIdAndActive(anyLong(), eq(true))).thenReturn(Set.of());
        when(exercise.getProblemStatement()).thenReturn("[task][Sort](<testid>999</testid>)");

        List<String> unresolved = taskService.findUnresolvedTaskTestReferences(exercise);

        assertThat(unresolved).hasSize(1).first().asString().contains("999");
    }
}
