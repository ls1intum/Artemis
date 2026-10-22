package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.ci.BulkTriggerThrottle;
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
        service = serviceWithBulkTriggerSettings(0, 0);
    }

    private static LocalCITriggerService serviceWithBulkTriggerSettings(int maxQueuedJobsPerBuildSlot, int maxWaitMinutes) {
        return new LocalCITriggerService(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, maxQueuedJobsPerBuildSlot, maxWaitMinutes);
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

    @Test
    void createBulkTriggerThrottle_withALimitOfZero_neverWaits() {
        // 0 is how an operator switches the wait off.
        assertThat(service.createBulkTriggerThrottle()).isSameAs(BulkTriggerThrottle.NONE);
    }

    @Test
    void createBulkTriggerThrottle_withALimit_createsAThrottleOfItsOwnForEveryRun() {
        service = serviceWithBulkTriggerSettings(2, 180);

        var first = service.createBulkTriggerThrottle();
        var second = service.createBulkTriggerThrottle();

        assertThat(first).isInstanceOf(LocalCIBulkTriggerThrottle.class).isNotSameAs(BulkTriggerThrottle.NONE);
        assertThat(second).as("a throttle remembers what its run already waited, so runs must not share one").isNotSameAs(first);
    }

    @Test
    void constructor_rejectsANegativeLimitOfQueuedJobsPerBuildSlot() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> serviceWithBulkTriggerSettings(-1, 180)).withMessageContaining("max-queued-jobs-per-build-slot")
                .withMessageContaining("-1");
    }

    @Test
    void constructor_rejectsANegativeMaximumWait() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> serviceWithBulkTriggerSettings(2, -1)).withMessageContaining("max-wait-minutes")
                .withMessageContaining("-1");
    }

    @Test
    void createBulkTriggerThrottle_withAMaximumWaitOfZero_isAccepted() {
        // 0 minutes is a valid way to say "never wait for room", unlike a negative number.
        assertThat(serviceWithBulkTriggerSettings(2, 0).createBulkTriggerThrottle()).isInstanceOf(LocalCIBulkTriggerThrottle.class);
    }
}
