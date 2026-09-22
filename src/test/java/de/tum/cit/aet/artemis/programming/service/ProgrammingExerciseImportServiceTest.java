package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/** Preparation must be separable from remote work so authoring can reserve the destination before exposing it. */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseImportServiceTest {

    @Mock
    private ProgrammingExerciseValidationService validation;

    @Mock
    private ProgrammingExerciseBuildPlanService plans;

    @Mock
    private ProgrammingExerciseCreationScheduleService scheduling;

    @Mock
    private ProgrammingExerciseTaskService tasks;

    @Mock
    private ProgrammingExerciseImportBasicService basic;

    @Mock
    private ProgrammingExerciseTestRepository exercises;

    private ProgrammingExerciseImportService service;

    private ProgrammingExercise source;

    private ProgrammingExercise destination;

    @BeforeEach
    void setUp() {
        service = new ProgrammingExerciseImportService(Optional.empty(), Optional.empty(), validation, plans, scheduling, tasks, basic, null, exercises, Optional.empty(), null);
        source = new ProgrammingExercise();
        source.setId(1L);
        destination = new ProgrammingExercise();
        Course course = new Course();
        course.setShortName("course");
        destination.setCourse(course);
        destination.setShortName("Variant-One");
    }

    @Test
    void preparationCreatesOnlyMetadataAndChecksIdentityBeforeSaving() {
        when(basic.importProgrammingExerciseBasis(any(), any(), any(), isNull())).thenAnswer(invocation -> {
            destination.setId(2L);
            return destination;
        });

        ProgrammingExercise prepared = service.prepareImport(source, new ProgrammingExerciseBuildConfig(), destination, null);

        assertThat(prepared).isSameAs(destination);
        assertThat(destination.getShortName()).isEqualTo("VariantOne");
        var order = inOrder(validation, basic);
        order.verify(validation).checkIfProjectExists(destination);
        order.verify(basic).importProgrammingExerciseBasis(any(), any(), any(), isNull());
        order.verifyNoMoreInteractions();
        verifyNoInteractions(plans, scheduling, tasks);
    }

    @Test
    void completionCopiesRepositoriesBeforeBuildPlansAndScheduling() {
        destination.setId(2L);

        assertThat(service.completeImport(source, destination, true, false)).isSameAs(destination);

        var order = inOrder(basic, plans, scheduling, tasks);
        order.verify(basic).importRepositories(source, destination);
        order.verify(plans).setupBuildPlansForNewExercise(destination);
        order.verify(scheduling).scheduleOperations(2L);
        order.verify(tasks).replaceTestIdsWithNames(destination);
        order.verifyNoMoreInteractions();
        verifyNoInteractions(validation);
    }

    @Test
    void ordinaryImportStillRunsBothPhasesInOrder() {
        when(basic.importProgrammingExerciseBasis(any(), any(), any(), isNull())).thenAnswer(invocation -> {
            destination.setId(2L);
            return destination;
        });

        assertThat(service.importProgrammingExercise(source, new ProgrammingExerciseBuildConfig(), destination, null, true, false)).isSameAs(destination);

        var order = inOrder(basic, plans, scheduling, tasks);
        order.verify(basic).importProgrammingExerciseBasis(any(), any(), any(), isNull());
        order.verify(basic).importRepositories(source, destination);
        order.verify(plans).setupBuildPlansForNewExercise(destination);
        order.verify(scheduling).scheduleOperations(2L);
        order.verify(tasks).replaceTestIdsWithNames(destination);
        order.verifyNoMoreInteractions();
    }
}
