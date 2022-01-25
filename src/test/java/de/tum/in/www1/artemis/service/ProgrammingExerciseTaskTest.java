package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTaskService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class ProgrammingExerciseTaskTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        this.programmingExercise = programmingExerciseRepository.findAll().get(0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void createValidTask() {
        var task = new ProgrammingExerciseTask().taskName("Test Task").exercise(this.programmingExercise);
        this.programmingExerciseTaskService.createProgrammingExerciseTask(task);
        assertThat(this.programmingExerciseTaskRepository.findByExerciseId(this.programmingExercise.getId()).contains(task));
    }

    @Test
    public void createInvalidTasks() {
        var invalidTask1 = new ProgrammingExerciseTask();
        assertThrows(BadRequestAlertException.class, () -> this.programmingExerciseTaskService.createProgrammingExerciseTask(invalidTask1));

        this.programmingExercise.setId(Long.MAX_VALUE);
        var invalidTask2 = new ProgrammingExerciseTask().exercise(this.programmingExercise);
        assertThrows(EntityNotFoundException.class, () -> this.programmingExerciseTaskService.createProgrammingExerciseTask(invalidTask2));
    }

    @Test
    public void deleteTask() {
        var task = new ProgrammingExerciseTask().taskName("Test Task").exercise(this.programmingExercise);
        this.programmingExerciseTaskService.createProgrammingExerciseTask(task);
        this.programmingExerciseTaskService.deleteProgrammingExerciseTask(task.getId());
        assertThat(!this.programmingExerciseTaskRepository.findByExerciseId(this.programmingExercise.getId()).contains(task));
    }

    @Test
    public void deleteNonExistentTask() {
        assertThrows(EntityNotFoundException.class, () -> this.programmingExerciseTaskService.deleteProgrammingExerciseTask(Long.MAX_VALUE));
    }

    @Test
    public void updateExistentTaskName() {
        var task = new ProgrammingExerciseTask().taskName("Test Task").exercise(this.programmingExercise);
        this.programmingExerciseTaskService.createProgrammingExerciseTask(task);

        var updatedTask = task.taskName("Updated Test Task");
        this.programmingExerciseTaskService.updateProgrammingExerciseTask(updatedTask, task.getId());
        assertThat(!this.programmingExerciseTaskRepository.findByExerciseId(this.programmingExercise.getId()).stream().findFirst().get().getTaskName().equals("Test Task"));
        assertThat(this.programmingExerciseTaskRepository.findByExerciseId(this.programmingExercise.getId()).stream().findFirst().get().getTaskName().equals("Updated Test Task"));
    }

    @Test
    public void updateTaskInvalid() {
        var task = new ProgrammingExerciseTask();
        assertThrows(BadRequestAlertException.class, () -> this.programmingExerciseTaskService.updateProgrammingExerciseTask(new ProgrammingExerciseTask(), task.getId()));

        task.exercise(this.programmingExercise);
        this.programmingExerciseTaskService.createProgrammingExerciseTask(task);
        assertThrows(BadRequestAlertException.class, () -> this.programmingExerciseTaskService.updateProgrammingExerciseTask(task, Long.MAX_VALUE));

        task.exercise(null);
        assertThrows(BadRequestAlertException.class, () -> this.programmingExerciseTaskService.updateProgrammingExerciseTask(task, task.getId()));
    }
}
