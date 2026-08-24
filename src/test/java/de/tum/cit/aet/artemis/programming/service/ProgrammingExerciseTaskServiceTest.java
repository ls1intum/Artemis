package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseTaskServiceTest extends AbstractProgrammingIntegrationIndependentTest {

    ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Mock
    ProgrammingExerciseTaskTestRepository programmingExerciseTaskRepository;

    @Mock
    ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @BeforeEach
    void setUp() {
        programmingExerciseTaskService = new ProgrammingExerciseTaskService(programmingExerciseTaskRepository, programmingExerciseTestCaseRepository);
    }

    @Test
    void verifyTaskReplacementInProblemStatementTest() {
        ProgrammingExerciseTestCase testCase1 = mock(ProgrammingExerciseTestCase.class);
        when(testCase1.getId()).thenReturn(1L);
        when(testCase1.getTestName()).thenReturn("TestTask");

        doReturn((Set.of(testCase1))).when(programmingExerciseTestCaseRepository).findByExerciseIdAndActive(1L, true);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProblemStatement("Some Problem Statement Text infront [task][Placeholder in Problem Statement](TestTask) More Text at the end"
                + "Only Problem Statement Text infront [task][Placeholder in Problem Statement](TestTask)"
                + "[task][Placeholder in Problem Statement](TestTask) Only Problem Statement Text at the end");
        exercise.setId(1L);

        final String problemStatement = String.format(
                "Some Problem Statement Text infront [task][Placeholder in Problem Statement](<testid>%d</testid>) More Text at the end"
                        + "Only Problem Statement Text infront [task][Placeholder in Problem Statement](<testid>%d</testid>)"
                        + "[task][Placeholder in Problem Statement](<testid>%d</testid>) Only Problem Statement Text at the end",
                testCase1.getId(), testCase1.getId(), testCase1.getId());
        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);
        assertThat(exercise.getProblemStatement()).isEqualTo(problemStatement);
    }

    @Test
    void shouldResolveTestCaseWhenTheIdWrapperIsNotAtTheStartOfTheReference() {
        // `testName<testid>1</testid>` is authored in practice, and the shared grammar in TestReferenceParser matches
        // the wrapper anywhere in the reference. Gating on a prefix instead would send such a reference through the
        // name lookup, so the task would silently lose the test case that the problem-statement renderer resolves by
        // id from the very same text.
        ProgrammingExerciseTestCase testCase = mock(ProgrammingExerciseTestCase.class);
        when(testCase.getId()).thenReturn(1L);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProblemStatement("[task][Sorting](testBubbleSort()<testid>1</testid>)");

        // No tasks stored yet, so every extracted task is a new one and reaches saveAll below.
        doReturn(Set.<ProgrammingExerciseTask>of()).when(programmingExerciseTaskRepository).findByExerciseIdWithTestCases(1L);
        doReturn(Set.of(testCase)).when(programmingExerciseTestCaseRepository).findByExerciseId(1L);

        programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);

        var saved = ArgumentCaptor.forClass(Set.class);
        verify(programmingExerciseTaskRepository).saveAll(saved.capture());
        assertThat((Set<ProgrammingExerciseTask>) saved.getValue()).singleElement().satisfies(task -> {
            assertThat(task.getTaskName()).isEqualTo("Sorting");
            assertThat(task.getTestCases()).containsExactly(testCase);
        });
    }

    @Test
    void verifyRegexCharacterEscapeForTaskReplacementTest() {
        ProgrammingExerciseTestCase testCase1 = mock(ProgrammingExerciseTestCase.class);
        when(testCase1.getId()).thenReturn(1L);
        when(testCase1.getTestName()).thenReturn("Outerclass$Innerclass#method");

        ProgrammingExerciseTestCase testCase2 = mock(ProgrammingExerciseTestCase.class);
        when(testCase2.getId()).thenReturn(2L);
        when(testCase2.getTestName()).thenReturn("someTestNameThatContains\\\\");

        doReturn((Set.of(testCase1, testCase2))).when(programmingExerciseTestCaseRepository).findByExerciseIdAndActive(1L, true);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProblemStatement(
                "[task][Placeholder in Problem Statement](Outerclass$Innerclass#method)" + "[task][Placeholder2 in Problem Statement](someTestNameThatContains\\\\)");
        exercise.setId(1L);

        final String problemStatement = String.format(
                "[task][Placeholder in Problem Statement](<testid>%d</testid>)" + "[task][Placeholder2 in Problem Statement](<testid>%d</testid>)", testCase1.getId(),
                testCase2.getId());
        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);
        assertThat(exercise.getProblemStatement()).isEqualTo(problemStatement);
    }

}
