package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class ProblemStatementMetadataUpdateTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private ProgrammingExerciseUtilService exercises;

    @Test
    void taskFailureRollsBackTheRepositoryOwnedMetadataTransaction() {
        var exercise = exercise();
        var tasks = mock(ProgrammingExerciseTaskService.class);
        doThrow(new IllegalStateException("task write failed")).when(tasks).updateTasksFromProblemStatement(any());
        var service = new ProblemStatementMetadataUpdateService(programmingExerciseRepository, tasks);

        assertThatThrownBy(() -> service.updateProblemStatementAndTasks(exercise, "new statement", "New title", "old statement", "Old title"))
                .hasRootCauseInstanceOf(IllegalStateException.class).hasMessageContaining("task write failed");

        var persisted = programmingExerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(persisted.getProblemStatement()).isEqualTo("old statement");
        assertThat(persisted.getTitle()).isEqualTo("Old title");
    }

    @Test
    void staleMetadataNeverStartsTaskSynchronization() {
        var exercise = exercise();
        var tasks = mock(ProgrammingExerciseTaskService.class);
        var service = new ProblemStatementMetadataUpdateService(programmingExerciseRepository, tasks);

        assertThat(service.updateProblemStatementAndTasks(exercise, "new statement", "New title", "stale statement", "Old title")).isZero();
        verifyNoInteractions(tasks);
        assertThat(programmingExerciseRepository.findByIdElseThrow(exercise.getId()).getProblemStatement()).isEqualTo("old statement");
    }

    private ProgrammingExercise exercise() {
        var course = exercises.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise.setTitle("Old title");
        exercise.setProblemStatement("old statement");
        return programmingExerciseRepository.save(exercise);
    }
}
