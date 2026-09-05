package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
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

    /** A test case whose name is what the instructor writes in the problem statement and whose id is what is stored. */
    private static ProgrammingExerciseTestCase testCase(long id, String testName) {
        ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase();
        testCase.setId(id);
        testCase.setTestName(testName);
        return testCase;
    }

    private ProgrammingExercise exerciseWith(String problemStatement, ProgrammingExerciseTestCase... testCases) {
        // Names are replaced with ids only for the tests that exist in the repository, while ids are replaced with names for all of them, so that a task referring to a
        // deleted test still shows what it referred to. The two directions therefore read the test cases differently.
        lenient().doReturn(Set.of(testCases)).when(programmingExerciseTestCaseRepository).findByExerciseIdAndActive(1L, true);
        lenient().doReturn(Set.of(testCases)).when(programmingExerciseTestCaseRepository).findByExerciseId(1L);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProblemStatement(problemStatement);
        return exercise;
    }

    /**
     * Instructors write test names, Artemis stores ids so that renaming a test does not break the problem statement, and editors are shown names again. A name that survives
     * that round trip unchanged is the whole point; anything else silently detaches a task from its test.
     */
    @Test
    void replacingNamesWithIdsAndBack_leavesTheProblemStatementAsItWas() {
        String problemStatement = "[task][Sorting](testBubbleSort()) and [task][Strategy](testStrategy)";
        ProgrammingExercise exercise = exerciseWith(problemStatement, testCase(1L, "testBubbleSort()"), testCase(2L, "testStrategy"));

        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);
        assertThat(exercise.getProblemStatement()).as("what is stored refers to the tests by id")
                .isEqualTo("[task][Sorting](<testid>1</testid>) and [task][Strategy](<testid>2</testid>)");

        programmingExerciseTaskService.replaceTestIdsWithNames(exercise);
        assertThat(exercise.getProblemStatement()).as("what the editor is shown is what the instructor wrote").isEqualTo(problemStatement);
    }

    /**
     * A parameterized test carries its arguments in its name, commas included. Splitting the captured group on every comma would tear such a name in half and detach the task
     * from its test.
     */
    @Test
    void replaceTestNamesWithIds_keepsAParameterizedTestNameTogether() {
        ProgrammingExercise exercise = exerciseWith("[task][Sorting](testInsert(InsertMock, 1), testClass[SortStrategy], testWithBraces())",
                testCase(1L, "testInsert(InsertMock, 1)"), testCase(2L, "testClass[SortStrategy]"), testCase(3L, "testWithBraces()"));

        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);

        assertThat(exercise.getProblemStatement()).as("the comma inside the arguments does not split the name")
                .isEqualTo("[task][Sorting](<testid>1</testid>,<testid>2</testid>,<testid>3</testid>)");
    }

    @Test
    void replaceTestNamesWithIds_leavesATestThatDoesNotExistAsItIs() {
        // An instructor can mistype a test name or delete the test afterwards. The task then has to keep the name so that the mistake stays visible in the editor.
        ProgrammingExercise exercise = exerciseWith("[task][Sorting](testBubbleSort(), testThatWasDeleted())", testCase(1L, "testBubbleSort()"));

        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);

        assertThat(exercise.getProblemStatement()).as("the test that exists is stored by id, the other one is kept verbatim")
                .isEqualTo("[task][Sorting](<testid>1</testid>,testThatWasDeleted())");
    }

    @Test
    void replaceTestIdsWithNames_forAnIdThatNoLongerExists_keepsTheIdVisible() {
        // The test case was deleted after the problem statement was saved. Dropping the reference would hide that a task no longer refers to anything.
        ProgrammingExercise exercise = exerciseWith("[task][Sorting](<testid>1</testid>,<testid>404</testid>)", testCase(1L, "testBubbleSort()"));

        programmingExerciseTaskService.replaceTestIdsWithNames(exercise);

        assertThat(exercise.getProblemStatement()).isEqualTo("[task][Sorting](testBubbleSort(),<testid>404</testid>)");
    }

    @Test
    void replaceTestNamesWithIds_alsoRewritesTheTestsColorReferencesInsideAPlantUmlDiagram() {
        // A class diagram colours its elements by the tests that cover them, referring to them the same way a task does, so both have to be kept in step.
        ProgrammingExercise exercise = exerciseWith("""
                @startuml
                class BubbleSort #testsColor(testBubbleSort()) {
                }
                @enduml
                """, testCase(1L, "testBubbleSort()"));

        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);

        assertThat(exercise.getProblemStatement()).as("the diagram refers to the test by id as well").contains("#testsColor(<testid>1</testid>)");
    }

    @Test
    void replaceTestNamesWithIds_forAProblemStatementWithoutTasks_changesNothing() {
        ProgrammingExercise exercise = exerciseWith("Implement the sorting strategies. There is no task here.", testCase(1L, "testBubbleSort()"));

        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);

        assertThat(exercise.getProblemStatement()).isEqualTo("Implement the sorting strategies. There is no task here.");
    }

    @Test
    void replaceTestNamesWithIds_forATaskWithoutAnyTest_leavesTheEmptyTaskAlone() {
        // A task an instructor has not attached a test to yet is legitimate while an exercise is being written.
        ProgrammingExercise exercise = exerciseWith("[task][Not wired up yet]()", testCase(1L, "testBubbleSort()"));

        programmingExerciseTaskService.replaceTestNamesWithIds(exercise);

        assertThat(exercise.getProblemStatement()).isEqualTo("[task][Not wired up yet]()");
    }
}
