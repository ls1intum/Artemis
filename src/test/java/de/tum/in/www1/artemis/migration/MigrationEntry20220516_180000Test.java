package de.tum.in.www1.artemis.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.migration.entries.MigrationEntry20220516_180000;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

class MigrationEntry20220516_180000Test extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private MigrationEntry20220516_180000 migrationEntry;

    @Autowired
    private ProgrammingExerciseTaskService taskService;

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    private ProgrammingExercise exercise;

    private ExerciseHint hint1;

    private ExerciseHint hint2;

    private ExerciseHint hint3;

    @BeforeEach
    void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        exercise = exerciseRepository.findAll().get(0);
        database.addHintsToExercise(exercise);
        var hints = exerciseHintRepository.findAll(Sort.by("id"));
        hint1 = hints.get(0);
        hint2 = hints.get(1);
        hint3 = hints.get(2);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    void testOneTaskWithOneHint() {
        var problemStatement = """
                [task][Task 1](test1){%s}
                """;
        problemStatement = problemStatement.formatted(hint1.getId());
        exercise.setProblemStatement(problemStatement);
        exerciseRepository.save(exercise);

        var tasks = taskService.updateTasksFromProblemStatement(exercise);
        assertThat(tasks).hasSize(1);
        var task = tasks.stream().findFirst().orElseThrow();

        migrationEntry.execute();

        // Check that the problem statement has been updated
        var updatedProblemStatement = exerciseRepository.findByIdElseThrow(exercise.getId()).getProblemStatement();
        assertThat(updatedProblemStatement).doesNotContain("{" + hint1.getId() + "}");

        // Check that the hints have been updated
        assertThat(exerciseHintRepository.findByIdElseThrow(hint1.getId()).getProgrammingExerciseTask().getId()).isEqualTo(task.getId());
        assertThat(exerciseHintRepository.findByIdElseThrow(hint2.getId()).getProgrammingExerciseTask()).isNull();
        assertThat(exerciseHintRepository.findByIdElseThrow(hint3.getId()).getProgrammingExerciseTask()).isNull();
    }

    @Test
    void testOneTaskWithThreeHints() {
        var problemStatement = """
                [task][Task 1](test1){%s, %s, %s  }
                """;
        problemStatement = problemStatement.formatted(hint1.getId(), hint2.getId(), hint3.getId());
        exercise.setProblemStatement(problemStatement);
        exerciseRepository.save(exercise);

        var tasks = taskService.updateTasksFromProblemStatement(exercise);
        assertThat(tasks).hasSize(1);
        var task = tasks.stream().findFirst().orElseThrow();

        migrationEntry.execute();

        // Check that the problem statement has been updated
        var updatedProblemStatement = exerciseRepository.findByIdElseThrow(exercise.getId()).getProblemStatement();
        assertThat(updatedProblemStatement).doesNotContain("{" + hint1.getId() + ", " + hint2.getId() + ", " + hint3.getId() + "  }");

        // Check that the hints have been updated
        assertThat(exerciseHintRepository.findByIdElseThrow(hint1.getId()).getProgrammingExerciseTask().getId()).isEqualTo(task.getId());
        assertThat(exerciseHintRepository.findByIdElseThrow(hint2.getId()).getProgrammingExerciseTask().getId()).isEqualTo(task.getId());
        assertThat(exerciseHintRepository.findByIdElseThrow(hint3.getId()).getProgrammingExerciseTask().getId()).isEqualTo(task.getId());
    }

    @Test
    void testThreeTasksWithOneHintEach() {
        var problemStatement = """
                [task][Task 1](test1){%s}
                [task][Task 2](test2){%s}
                [task][Task 3](test3){%s}
                """;
        problemStatement = problemStatement.formatted(hint1.getId(), hint2.getId(), hint3.getId());
        exercise.setProblemStatement(problemStatement);
        exerciseRepository.save(exercise);

        var tasks = new ArrayList<>(taskService.updateTasksFromProblemStatement(exercise));
        assertThat(tasks).hasSize(3);
        tasks.sort(Comparator.comparing(ProgrammingExerciseTask::getTaskName));
        var task1 = tasks.get(0);
        var task2 = tasks.get(1);
        var task3 = tasks.get(2);

        migrationEntry.execute();

        // Check that the problem statement has been updated
        var updatedProblemStatement = exerciseRepository.findByIdElseThrow(exercise.getId()).getProblemStatement();
        assertThat(updatedProblemStatement).doesNotContain("{" + hint1.getId() + "}", "{" + hint2.getId() + "}", "{" + hint3.getId() + "}");

        // Check that the hints have been updated
        assertThat(exerciseHintRepository.findByIdElseThrow(hint1.getId()).getProgrammingExerciseTask().getId()).isEqualTo(task1.getId());
        assertThat(exerciseHintRepository.findByIdElseThrow(hint2.getId()).getProgrammingExerciseTask().getId()).isEqualTo(task2.getId());
        assertThat(exerciseHintRepository.findByIdElseThrow(hint3.getId()).getProgrammingExerciseTask().getId()).isEqualTo(task3.getId());
    }
}
