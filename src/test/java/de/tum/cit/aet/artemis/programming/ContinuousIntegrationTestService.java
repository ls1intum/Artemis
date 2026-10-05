package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.util.MockDelegate;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ContinuousIntegrationTestService {

    @Value("${artemis.continuous-integration.url}")
    private URI ciServerUrl;

    private ProgrammingExerciseStudentParticipation participation;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private MockDelegate mockDelegate;

    private ContinuousIntegrationService continuousIntegrationService;

    public ProgrammingExercise programmingExercise;

    /**
     * Initializes the test case with a programming exercise and a student participation whose repository is a real LocalVC repository.
     */
    public void setup(String testPrefix, MockDelegate mockDelegate, ContinuousIntegrationService continuousIntegrationService) throws Exception {
        this.mockDelegate = mockDelegate;
        this.continuousIntegrationService = continuousIntegrationService;

        userUtilService.addUsers(testPrefix, 2, 0, 0, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(testPrefix);
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();

        String login = testPrefix + "student1";
        participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, login);
        assertThat(programmingExercise).as("Exercise was correctly set").isEqualTo(participation.getProgrammingExercise());
    }

    public ProgrammingExerciseStudentParticipation getParticipation() {
        return participation;
    }

    public void testGetBuildStatusNotFound() throws Exception {
        mockDelegate.mockGetBuildPlan(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), false, false, false, false);

        // INACTIVE // same as not found
        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive1() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, false, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive2() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, true, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusQueued() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, false, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is queued").isEqualTo(ContinuousIntegrationService.BuildStatus.QUEUED);
    }

    public void testGetBuildStatusBuilding() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, true, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is building").isEqualTo(ContinuousIntegrationService.BuildStatus.BUILDING);
    }

    public void testGetBuildStatusFails() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, true, true);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testHealthRunning() throws Exception {
        mockDelegate.mockHealthInCiService(true, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.additionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isTrue();
    }

    public void testHealthNotRunning() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.additionalInfo().get("url")).isEqualTo(ciServerUrl);
        assertThat(health.additionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isFalse();
    }

    public void testHealthException() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.INTERNAL_SERVER_ERROR);
        var health = continuousIntegrationService.health();
        assertThat(health.additionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isFalse();
        assertThat(health.exception()).isNotNull();
    }
}
