package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

/**
 * Unit tests for {@link ProgrammingExerciseTaskService#replaceTestNamesWithIds} and
 * {@link ProgrammingExerciseTaskService#findUnresolvedTaskTestReferences}. Unlike {@code extractTasks}, which silently
 * drops task-marker test references that don't resolve to a real test case, the latter reports them so a caller can
 * surface a precise error (e.g. a variant-generation verify gate) instead of a silently-broken task-test link.
 */
class ProgrammingExerciseTaskServiceTest {

    private static final long EXERCISE_ID = 42L;

    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    private ProgrammingExerciseTaskService taskService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        ProgrammingExerciseTaskRepository taskRepository = mock(ProgrammingExerciseTaskRepository.class);
        testCaseRepository = mock(ProgrammingExerciseTestCaseTestRepository.class);
        taskService = new ProgrammingExerciseTaskService(taskRepository, testCaseRepository);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(EXERCISE_ID);
    }

    private void withTestCases(String... names) {
        Set<ProgrammingExerciseTestCase> testCases = Arrays.stream(names).map(name -> new ProgrammingExerciseTestCase().testName(name).id((long) name.hashCode()))
                .collect(Collectors.toSet());
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
    void shouldIgnoreATaskMarkerWithAWhitespaceOnlyReferenceList() {
        withTestCases("testBubbleSort");
        when(exercise.getProblemStatement()).thenReturn("[task][Sort](   )");

        assertThat(taskService.findUnresolvedTaskTestReferences(exercise)).isEmpty();
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

    @Test
    void verifyTaskReplacementInProblemStatementTest() {
        ProgrammingExerciseTestCase testCase1 = mock(ProgrammingExerciseTestCase.class);
        when(testCase1.getId()).thenReturn(1L);
        when(testCase1.getTestName()).thenReturn("TestTask");

        doReturn((Set.of(testCase1))).when(testCaseRepository).findByExerciseIdAndActive(1L, true);

        // A real exercise, not the shared mock: replaceTestNamesWithIds writes the result back via setProblemStatement.
        ProgrammingExercise realExercise = new ProgrammingExercise();
        realExercise.setProblemStatement("Some Problem Statement Text infront [task][Placeholder in Problem Statement](TestTask) More Text at the end"
                + "Only Problem Statement Text infront [task][Placeholder in Problem Statement](TestTask)"
                + "[task][Placeholder in Problem Statement](TestTask) Only Problem Statement Text at the end");
        realExercise.setId(1L);

        final String problemStatement = String.format(
                "Some Problem Statement Text infront [task][Placeholder in Problem Statement](<testid>%d</testid>) More Text at the end"
                        + "Only Problem Statement Text infront [task][Placeholder in Problem Statement](<testid>%d</testid>)"
                        + "[task][Placeholder in Problem Statement](<testid>%d</testid>) Only Problem Statement Text at the end",
                testCase1.getId(), testCase1.getId(), testCase1.getId());
        taskService.replaceTestNamesWithIds(realExercise);
        assertThat(realExercise.getProblemStatement()).isEqualTo(problemStatement);
    }

    @Test
    void verifyRegexCharacterEscapeForTaskReplacementTest() {
        ProgrammingExerciseTestCase testCase1 = mock(ProgrammingExerciseTestCase.class);
        when(testCase1.getId()).thenReturn(1L);
        when(testCase1.getTestName()).thenReturn("Outerclass$Innerclass#method");

        ProgrammingExerciseTestCase testCase2 = mock(ProgrammingExerciseTestCase.class);
        when(testCase2.getId()).thenReturn(2L);
        when(testCase2.getTestName()).thenReturn("someTestNameThatContains\\\\");

        doReturn((Set.of(testCase1, testCase2))).when(testCaseRepository).findByExerciseIdAndActive(1L, true);

        ProgrammingExercise realExercise = new ProgrammingExercise();
        realExercise.setProblemStatement(
                "[task][Placeholder in Problem Statement](Outerclass$Innerclass#method)" + "[task][Placeholder2 in Problem Statement](someTestNameThatContains\\\\)");
        realExercise.setId(1L);

        final String problemStatement = String.format(
                "[task][Placeholder in Problem Statement](<testid>%d</testid>)" + "[task][Placeholder2 in Problem Statement](<testid>%d</testid>)", testCase1.getId(),
                testCase2.getId());
        taskService.replaceTestNamesWithIds(realExercise);
        assertThat(realExercise.getProblemStatement()).isEqualTo(problemStatement);
    }
}
