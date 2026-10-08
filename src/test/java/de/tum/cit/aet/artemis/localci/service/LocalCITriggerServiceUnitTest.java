package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Unit tests for guards in {@link LocalCITriggerService} that do not need a Spring context.
 */
class LocalCITriggerServiceUnitTest {

    private LocalCITriggerService service;

    @BeforeEach
    void setUp() {
        service = new LocalCITriggerService(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void getRepositoryInfoFailsIfTheRepositoryUriIsMissing() {
        var exercise = new ProgrammingExercise();
        exercise.setId(1L);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(5L);
        participation.setProgrammingExercise(exercise);

        assertThatExceptionOfType(LocalCIException.class)
                .isThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "getRepositoryInfo", participation, RepositoryType.USER, new ProgrammingExerciseBuildConfig()))
                .withMessageContaining("participation 5");
    }

    @Test
    void testCoursePenaltyIsAppliedOnlyForTestCourses() {
        var course = new Course();
        course.setTestCourse(true);

        Integer penalized = ReflectionTestUtils.invokeMethod(service, "addPenaltyIfTestCourse", course, 4);
        course.setTestCourse(false);
        Integer regular = ReflectionTestUtils.invokeMethod(service, "addPenaltyIfTestCourse", course, 4);

        assertThat(penalized).isGreaterThan(4);
        assertThat(regular).isEqualTo(4);
    }
}
