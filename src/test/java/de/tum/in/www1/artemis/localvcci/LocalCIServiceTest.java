package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService.BuildStatus;

class LocalCIServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localciservice";

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReturnCorrectBuildStatus() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.INACTIVE);

        participation.setBuildPlanId("MY-PLAN_QUEUED");
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.QUEUED);

        participation.setBuildPlanId("MY-PLAN_BUILDING");
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.BUILDING);
    }

    @Test
    void testHealth() {
        var health = continuousIntegrationService.health();
        assertThat(health.isUp()).isTrue();
    }

    @Test
    void testUnsupportedMethods() {
        assertThat(continuousIntegrationService.retrieveLatestArtifact(null)).isNull();
    }
}
