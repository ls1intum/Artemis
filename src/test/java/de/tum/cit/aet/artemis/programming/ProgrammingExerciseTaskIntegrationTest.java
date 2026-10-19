package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTaskDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseResponseDTO;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;

class ProgrammingExerciseTaskIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexercisetask";

    @Autowired
    private ProgrammingExerciseTaskTestRepository programmingExerciseTaskTestRepository;

    private ProgrammingExercise programmingExercise;

    /** The task that {@code addTasksToProgrammingExercise} created for the inactive test case ("test2"). */
    private ProgrammingExerciseTask taskWithInactiveTestCase;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        // Needed, as addTasksToProgrammingExercise reads the exercise's (eagerly loaded) test cases
        programmingExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(programmingExercise);
        // One task per test case, including one for the inactive "test2" (see ProgrammingExerciseUtilService#addTestCasesToProgrammingExercise).
        // addTasksToProgrammingExercise() sets programmingExercise.tasks directly to the freshly created (non-duplicated) list - deliberately NOT
        // reloading the exercise afterwards, since findOneWithEagerEverything() JOIN FETCHes several collections at once (testCases, tasks,
        // tasks.testCases, gradingCriteria, ...) without DISTINCT and would hand back a Cartesian-product-duplicated tasks list.
        programmingExerciseUtilService.addTasksToProgrammingExercise(programmingExercise);

        taskWithInactiveTestCase = programmingExercise.getTasks().stream().filter(task -> task.getTestCases().stream().anyMatch(testCase -> !testCase.isActive())).findFirst()
                .orElseThrow();
    }

    /**
     * {@code ProgrammingExerciseTaskDTO} is {@code @JsonInclude(NON_EMPTY)}: an empty {@code testCases} list is
     * dropped from the wire and comes back as {@code null} after deserialization. Normalizes an in-memory-built
     * expectation the same way before comparing it against a response that went through the wire.
     */
    private static ProgrammingExerciseTaskDTO normalizedForWireComparison(ProgrammingExerciseTaskDTO dto) {
        if (dto.testCases() != null && dto.testCases().isEmpty()) {
            return new ProgrammingExerciseTaskDTO(dto.id(), dto.taskName(), null);
        }
        return dto;
    }

    private String tasksUrl() {
        return "/api/programming/programming-exercises/" + programmingExercise.getId() + "/tasks";
    }

    private String tasksWithUnassignedUrl() {
        return "/api/programming/programming-exercises/" + programmingExercise.getId() + "/tasks-with-unassigned-test-cases";
    }

    /**
     * Snapshot of the {@code programming_exercise_task_test_case} join rows for the exercise's tasks, as
     * {@code (taskId, testCaseId)} pairs. The test runs without a transaction, so each repository call reads
     * through a session of its own - the rows come from the database, not from an already-managed (and possibly
     * in-place-mutated) collection.
     */
    private Set<List<Long>> taskTestCaseJoinRows() {
        return programmingExerciseTaskTestRepository.findByExerciseIdWithTestCases(programmingExercise.getId()).stream()
                .flatMap(task -> task.getTestCases().stream().map(testCase -> List.of(task.getId(), testCase.getId()))).collect(Collectors.toSet());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTasks_asTutor_excludesInactiveTestCasesAndDoesNotMutateJoinTable() throws Exception {
        var joinRowsBefore = taskTestCaseJoinRows();

        Set<ProgrammingExerciseTaskDTO> response = request.getSet(tasksUrl(), HttpStatus.OK, ProgrammingExerciseTaskDTO.class);

        Set<ProgrammingExerciseTaskDTO> expected = programmingExercise.getTasks().stream().map(ProgrammingExerciseTaskDTO::ofWithoutInactiveTestCases)
                .map(ProgrammingExerciseTaskIntegrationTest::normalizedForWireComparison).collect(Collectors.toSet());
        assertThat(response).containsExactlyInAnyOrderElementsOf(expected);

        // The task that owned the inactive test case now has no test cases in the response...
        ProgrammingExerciseTaskDTO taskWithInactiveResponse = response.stream().filter(task -> task.id().equals(taskWithInactiveTestCase.getId())).findFirst().orElseThrow();
        assertThat(taskWithInactiveResponse.testCases()).isNullOrEmpty();
        // ...and every other returned test case is active.
        assertThat(response).filteredOn(task -> task.testCases() != null).flatExtracting(ProgrammingExerciseTaskDTO::testCases)
                .extracting(ProgrammingExerciseTestCaseResponseDTO::active).containsOnly(true);

        // ...but the underlying join row for the inactive test case must survive untouched.
        var joinRowsAfter = taskTestCaseJoinRows();
        assertThat(joinRowsAfter).containsExactlyInAnyOrderElementsOf(joinRowsBefore);
        assertThat(joinRowsAfter).contains(List.of(taskWithInactiveTestCase.getId(), taskWithInactiveTestCase.getTestCases().iterator().next().getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTasks_asStudent_forbidden() throws Exception {
        request.getSet(tasksUrl(), HttpStatus.FORBIDDEN, ProgrammingExerciseTaskDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTasksWithUnassignedTestCases_asTutor_includesSyntheticNullIdTaskAndDoesNotMutateJoinTable() throws Exception {
        var unassignedTestCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testUnassigned");
        var joinRowsBefore = taskTestCaseJoinRows();

        List<ProgrammingExerciseTaskDTO> response = request.getList(tasksWithUnassignedUrl(), HttpStatus.OK, ProgrammingExerciseTaskDTO.class);

        // One DTO per pre-existing task (unfiltered - the inactive test case's task keeps it) plus the synthetic unassigned task.
        assertThat(response).hasSize(programmingExercise.getTasks().size() + 1);

        Set<ProgrammingExerciseTaskDTO> expectedAssignedTasks = programmingExercise.getTasks().stream().map(ProgrammingExerciseTaskDTO::of).collect(Collectors.toSet());
        List<ProgrammingExerciseTaskDTO> assignedTasksInResponse = response.stream().filter(task -> task.id() != null).toList();
        assertThat(assignedTasksInResponse).containsExactlyInAnyOrderElementsOf(expectedAssignedTasks);

        ProgrammingExerciseTaskDTO unassignedTaskDTO = response.stream().filter(task -> task.id() == null).findFirst().orElseThrow();
        assertThat(unassignedTaskDTO.taskName()).isEqualTo("Not assigned to task");
        assertThat(unassignedTaskDTO.testCases()).containsExactly(ProgrammingExerciseTestCaseResponseDTO.of(unassignedTestCase));

        var joinRowsAfter = taskTestCaseJoinRows();
        assertThat(joinRowsAfter).containsExactlyInAnyOrderElementsOf(joinRowsBefore);
        assertThat(joinRowsAfter).contains(List.of(taskWithInactiveTestCase.getId(), taskWithInactiveTestCase.getTestCases().iterator().next().getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTasksWithUnassignedTestCases_asStudent_forbidden() throws Exception {
        request.getList(tasksWithUnassignedUrl(), HttpStatus.FORBIDDEN, ProgrammingExerciseTaskDTO.class);
    }
}
