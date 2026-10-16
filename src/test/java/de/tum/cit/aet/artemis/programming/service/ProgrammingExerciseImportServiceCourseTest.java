package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for {@link ProgrammingExerciseImportService}: the name of the copied build plans contains the course short name, so a target exercise whose course cannot be
 * resolved must be rejected with a clear message before any build plan is touched.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseImportServiceCourseTest {

    @Mock
    private ContinuousIntegrationService continuousIntegrationService;

    @Mock
    private ProgrammingExerciseValidationService programmingExerciseValidationService;

    @Mock
    private ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService;

    @Mock
    private ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService;

    @Mock
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Mock
    private ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    @Mock
    private ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private ProgrammingExerciseImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ProgrammingExerciseImportService(Optional.of(continuousIntegrationService), Optional.empty(), programmingExerciseValidationService,
                programmingExerciseBuildPlanService, programmingExerciseCreationScheduleService, programmingExerciseTaskService, programmingExerciseImportBasicService,
                programmingExerciseTestCaseRepository, programmingExerciseRepository, Optional.empty(), programmingExerciseBuildConfigRepository);
    }

    @Test
    void importBuildPlans_forATargetExerciseWithoutCourse_isRejectedWithTheExerciseId() {
        var sourceExercise = new ProgrammingExercise();
        var newExercise = new ProgrammingExercise();
        newExercise.setId(4711L);
        newExercise.setTitle("Orphan");

        assertThatThrownBy(() -> importService.importBuildPlans(sourceExercise, newExercise)).isInstanceOf(IllegalStateException.class).hasMessageContaining("4711");
    }
}
