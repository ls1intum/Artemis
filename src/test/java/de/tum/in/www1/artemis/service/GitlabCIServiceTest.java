package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.util.Optional;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.PipelineStatus;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Trigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.GitLabCIException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;

class GitlabCIServiceTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    private static final String TEST_PREFIX = "gitlabciservicetest";

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private Long programmingExerciseId;

    @BeforeEach
    void initTestCase() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        database.addUsers(TEST_PREFIX, 2, 2, 0, 2);
        var course = database.addCourseWithOneProgrammingExercise();
        programmingExerciseId = database.getFirstExerciseWithType(course, ProgrammingExercise.class).getId();
    }

    @AfterEach
    void tearDown() throws Exception {
        super.resetMockProvider();
        super.resetSpyBeans();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testHealth() {
        var health = continuousIntegrationService.health();
        assertThat(health.isUp()).isTrue();
        assertThat(health.getAdditionalInfo()).containsEntry("cf.", "Version Control Server").containsEntry("url", gitlabServerUrl);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildStatusQueued() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockGetBuildStatus(PipelineStatus.CREATED);

        var result = continuousIntegrationService.getBuildStatus(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.QUEUED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildStatusBuilding() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockGetBuildStatus(PipelineStatus.RUNNING);

        var result = continuousIntegrationService.getBuildStatus(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.BUILDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildStatusInactive() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockGetBuildStatus(PipelineStatus.CANCELED);

        var result = continuousIntegrationService.getBuildStatus(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTriggerBuildSuccess() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        exercise.setBranch("main");
        programmingExerciseRepository.save(exercise);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockTriggerBuild(null);

        continuousIntegrationService.triggerBuild(participation);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), atLeastOnce()).createPipelineTrigger(any(), anyString());
        verify(gitlab.getPipelineApi(), times(1)).triggerPipeline(eq(urlService.getRepositoryPathFromRepositoryUrl(participation.getVcsRepositoryUrl())), any(Trigger.class),
                anyString(), isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTriggerBuildFails() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockTriggerBuildFailed(null);

        assertThatThrownBy(() -> continuousIntegrationService.triggerBuild(participation)).isInstanceOf(GitLabCIException.class);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), never()).triggerPipeline(eq(urlService.getRepositoryPathFromRepositoryUrl(participation.getVcsRepositoryUrl())), any(Trigger.class),
                anyString(), isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConfigureBuildPlanSuccess() throws Exception {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockConfigureBuildPlan(participation, defaultBranch);
        continuousIntegrationService.configureBuildPlan(participation, defaultBranch);
        verifyMocks();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConfigureBuildPlanFails() throws GitLabApiException {
        mockAddBuildPlanToGitLabRepositoryConfiguration(true);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl("http://some.test.url/PROJECTNAME/REPONAME-exercise.git");
        assertThatThrownBy(() -> continuousIntegrationService.configureBuildPlan(participation, "main")).isInstanceOf(GitLabCIException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateBuildPlanForExercise() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        final String repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(participation.getVcsRepositoryUrl());
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);

        continuousIntegrationService.createBuildPlanForExercise(exercise, "TEST-EXERCISE", participation.getVcsRepositoryUrl(), null, null);

        verify(gitlab, atLeastOnce()).getProjectApi();
        verify(gitlab.getProjectApi(), atLeastOnce()).getProject(eq(repositoryPath));
        verify(gitlab.getProjectApi(), atLeastOnce()).updateProject(any(Project.class));
        verify(gitlab.getProjectApi(), atLeastOnce()).createVariable(anyString(), anyString(), anyString(), any(), anyBoolean(), anyBoolean());
        assertThat(exercise.getBuildPlan()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCopyBuildPlan() {
        final String targetProjectKey = "TARGETPROJECTKEY";
        final String targetPlanName1 = "TARGETPLANNAME1";
        final String targetPlanName2 = "target-plan-name-#2";

        final String expectedBuildPlanKey1 = "TARGETPROJECTKEY-TARGETPLANNAME1";
        final String expectedBuildPlanKey2 = "TARGETPROJECTKEY-TARGETPLANNAME2";

        assertThat(continuousIntegrationService.copyBuildPlan(null, null, targetProjectKey, null, targetPlanName1, false)).isEqualTo(expectedBuildPlanKey1);
        assertThat(continuousIntegrationService.copyBuildPlan(null, null, targetProjectKey, null, targetPlanName2, false)).isEqualTo(expectedBuildPlanKey2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUnsupportedMethods() {
        continuousIntegrationService.createProjectForExercise(null);
        continuousIntegrationService.removeAllDefaultProjectPermissions(null);
        continuousIntegrationService.givePlanPermissions(null, null);
        continuousIntegrationService.giveProjectPermissions(null, null, null);
        continuousIntegrationService.updatePlanRepository(null, null, null, null, null, null, null, Optional.empty());
        continuousIntegrationService.enablePlan(null, null);
        continuousIntegrationService.deleteBuildPlan(null, null);
        continuousIntegrationService.deleteProject(null);
        continuousIntegrationService.performEmptySetupCommit(null);
        assertThat(continuousIntegrationService.getWebHookUrl(null, null)).isNotPresent();
        assertThat(continuousIntegrationService.checkIfProjectExists(null, null)).isNull();
        assertThat(continuousIntegrationService.checkIfBuildPlanExists(null, null)).isTrue();
        assertThat(continuousIntegrationService.getLatestBuildLogs(null)).isNull();
        assertThat(continuousIntegrationService.retrieveLatestArtifact(null)).isNull();
    }
}
