package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService.BuildStatus;
import de.tum.in.www1.artemis.util.ModelFactory;

public class LocalCIServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    void testReturnCorrectBuildStatus() {
        ProgrammingExercise exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), new Course());
        User user = ModelFactory.generateActivatedUser("student1");
        ProgrammingExerciseStudentParticipation participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, exercise, user);
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.INACTIVE);

        participation.setBuildPlanId("MY-PLAN_QUEUED");
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.QUEUED);

        participation.setBuildPlanId("MY-PLAN_BUILDING");
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.BUILDING);
    }
}
