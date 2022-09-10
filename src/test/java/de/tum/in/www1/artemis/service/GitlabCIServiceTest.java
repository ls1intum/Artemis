package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

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

public class GitlabCIServiceTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private Long programmingExerciseId;

    @BeforeEach
    void initTestCase() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        database.addUsers(2, 2, 0, 2);
        database.addCourseWithOneProgrammingExercise();
        programmingExerciseId = programmingExerciseRepository.findAll().get(0).getId();
    }

    @AfterEach
    void tearDown() throws IOException {
        super.resetMockProvider();
        super.resetSpyBeans();
        database.resetDatabase();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testHealth() throws Exception {
        var health = continuousIntegrationService.health();
        assertThat(health.isUp()).isTrue();
        assertThat(health.getAdditionalInfo()).containsEntry("cf.", "Version Control Server").containsEntry("url", gitlabServerUrl);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testGetWebHookUrl() {
        // TODO: adapt when actual implementation has been added
        var result = continuousIntegrationService.getWebHookUrl(null, null);
        assertThat(result).isNotPresent();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testGetBuildStatusQueued() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        mockGetBuildStatus(PipelineStatus.CREATED);

        var result = continuousIntegrationService.getBuildStatus(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.QUEUED);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testGetBuildStatusBuilding() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        mockGetBuildStatus(PipelineStatus.RUNNING);

        var result = continuousIntegrationService.getBuildStatus(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.BUILDING);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testGetBuildStatusInactive() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        mockGetBuildStatus(PipelineStatus.CANCELED);

        var result = continuousIntegrationService.getBuildStatus(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testTriggerBuildSuccess() throws GitLabApiException, URISyntaxException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        exercise.setBranch("main");
        programmingExerciseRepository.save(exercise);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        mockTriggerBuild(null);

        continuousIntegrationService.triggerBuild(participation);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), atLeastOnce()).createPipelineTrigger(any(), anyString());
        verify(gitlab.getPipelineApi(), times(1)).triggerPipeline(eq(urlService.getRepositoryPathFromRepositoryUrl(participation.getVcsRepositoryUrl())), any(Trigger.class),
                anyString(), isNull());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testTriggerBuildFails() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        mockTriggerBuildFailed(null);

        assertThatThrownBy(() -> continuousIntegrationService.triggerBuild(participation)).isInstanceOf(GitLabCIException.class);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), never()).triggerPipeline(eq(urlService.getRepositoryPathFromRepositoryUrl(participation.getVcsRepositoryUrl())), any(Trigger.class),
                anyString(), isNull());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testConfigureBuildPlanSuccess() throws Exception {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        mockConfigureBuildPlan(participation, defaultBranch);
        continuousIntegrationService.configureBuildPlan(participation, defaultBranch);
        verifyMocks();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testConfigureBuildPlanFails() throws GitLabApiException {
        mockAddBuildPlanToGitLabRepositoryConfiguration(true);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl("http://some.test.url/PROJECTNAME/REPONAME-exercise.git");
        assertThatThrownBy(() -> continuousIntegrationService.configureBuildPlan(participation, "main")).isInstanceOf(GitLabCIException.class);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testCreateBuildPlanForExercise() throws GitLabApiException, URISyntaxException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        final String repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(participation.getVcsRepositoryUrl());
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);

        continuousIntegrationService.createBuildPlanForExercise(exercise, null, participation.getVcsRepositoryUrl(), null, null);

        verify(gitlab, atLeastOnce()).getProjectApi();
        verify(gitlab.getProjectApi(), atLeastOnce()).getProject(eq(repositoryPath));
        verify(gitlab.getProjectApi(), atLeastOnce()).updateProject(any(Project.class));
        verify(gitlab.getProjectApi(), atLeastOnce()).createVariable(anyString(), anyString(), anyString(), any(), anyBoolean(), anyBoolean());
        assertThat(exercise.getBuildPlan()).isNotNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testUnsupportedMethods() {
        continuousIntegrationService.createProjectForExercise(null);
        continuousIntegrationService.removeAllDefaultProjectPermissions(null);
        continuousIntegrationService.givePlanPermissions(null, null);
        continuousIntegrationService.giveProjectPermissions(null, null, null);
        continuousIntegrationService.updatePlanRepository(null, null, null, null, null, null, null, null);
        continuousIntegrationService.enablePlan(null, null);
        continuousIntegrationService.deleteBuildPlan(null, null);
        continuousIntegrationService.deleteProject(null);
        continuousIntegrationService.performEmptySetupCommit(null);
        assertThat(continuousIntegrationService.checkIfProjectExists(null, null)).isNull();
        assertThat(continuousIntegrationService.checkIfBuildPlanExists(null, null)).isFalse();
        assertThat(continuousIntegrationService.getPlanKey(null)).isNull();
        assertThat(continuousIntegrationService.copyBuildPlan(null, null, null, null, null, false)).isNull();
        assertThat(continuousIntegrationService.convertBuildResult(null)).isNull();
        assertThat(continuousIntegrationService.getLatestBuildLogs(null)).isNull();
        assertThat(continuousIntegrationService.retrieveLatestArtifact(null)).isNull();
    }
}
