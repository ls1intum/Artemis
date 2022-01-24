package de.tum.in.www1.artemis.hestia;

import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;

public class ProgrammingExerciseSolutionEntryIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseTask task;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        exercise = exerciseRepository.findAll().get(0);
        var testCases = programmingExerciseTestCaseRepository.findAll();
        for (ProgrammingExerciseTestCase testCase : testCases) {
            var solutionEntry = new ProgrammingExerciseSolutionEntry().testCase(testCase).code("Some code").line(1).file("code.java");
            programmingExerciseSolutionEntryRepository.save(solutionEntry);
        }
        task = new ProgrammingExerciseTask().exercise(exercise).taskName("Task").testCases(new HashSet<>(testCases));
        task = programmingExerciseTaskRepository.save(task);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllSolutionEntriesForAnExerciseAsAStudent() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/programming-exercise-solution-entries", HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void queryAllSolutionEntriesForAnExerciseAsATutor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/programming-exercise-solution-entries", HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllSolutionEntriesForATaskAsAnInstructor() throws Exception {
        request.getList("/api/programming-exercise-tasks/" + task.getId() + "/programming-exercise-solution-entries", HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllSolutionEntriesForATaskAsAStudent() throws Exception {
        request.getList("/api/programming-exercise-tasks/" + task.getId() + "/programming-exercise-solution-entries", HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void queryAllSolutionEntriesForATaskAsATutor() throws Exception {
        request.getList("/api/programming-exercise-tasks/" + task.getId() + "/programming-exercise-solution-entries", HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllSolutionEntriesForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/programming-exercise-solution-entries", HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getSolutionEntryForAnExerciseAsStudentShouldReturnForbidden() throws Exception {
        ProgrammingExerciseSolutionEntry solutionEntry = programmingExerciseSolutionEntryRepository.findAll().get(0);
        request.get("/api/programming-exercise-solution-entries/" + solutionEntry.getId(), HttpStatus.FORBIDDEN, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getSolutionEntryForAnExerciseAsTutorForbidden() throws Exception {
        ProgrammingExerciseSolutionEntry solutionEntry = programmingExerciseSolutionEntryRepository.findAll().get(0);
        request.get("/api/programming-exercise-solution-entries/" + solutionEntry.getId(), HttpStatus.FORBIDDEN, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getSolutionEntryForAnExerciseAsEditor() throws Exception {
        ProgrammingExerciseSolutionEntry solutionEntry = programmingExerciseSolutionEntryRepository.findAll().get(0);
        request.get("/api/programming-exercise-solution-entries/" + solutionEntry.getId(), HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getSolutionEntryForAnExerciseAsAnInstructor() throws Exception {
        ProgrammingExerciseSolutionEntry solutionEntry = programmingExerciseSolutionEntryRepository.findAll().get(0);
        request.get("/api/programming-exercise-solution-entries/" + solutionEntry.getId(), HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
        request.get("/api/programming-exercise-solution-entries/" + 0L, HttpStatus.NOT_FOUND, ProgrammingExerciseSolutionEntry.class);
    }
}
