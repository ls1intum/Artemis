package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Trigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;
import de.tum.in.www1.artemis.ContinuousIntegrationTestService;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.GitLabCIException;

class GitlabCIServiceTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Autowired
    private ContinuousIntegrationTestService continuousIntegrationTestService;

    @BeforeEach
    void initTestCase() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        continuousIntegrationTestService.setup(this, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws IOException {
        database.resetDatabase();
        super.resetMockProvider();
        super.resetSpyBeans();
        continuousIntegrationTestService.tearDown();
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
    void testGetBuildStatus() {
        // TODO
        var result = continuousIntegrationService.getBuildStatus(null);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testTriggerBuildSuccess() throws GitLabApiException, URISyntaxException {
        final String repositoryUrl = "http://some.test.url/PROJECTNAME/REPONAME-exercise.git";
        final String repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl(repositoryUrl));
        final ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl(repositoryUrl);
        mockTriggerBuild(null);

        continuousIntegrationService.triggerBuild(participation);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), times(1)).triggerPipeline(eq(repositoryPath), any(Trigger.class), anyString(), any());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testTriggerBuildFails() throws GitLabApiException, URISyntaxException {
        final String repositoryUrl = "http://some.test.url/PROJECTNAME/REPONAME-exercise.git";
        final String repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl(repositoryUrl));
        final ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl(repositoryUrl);
        mockTriggerBuildFailed(null);

        assertThatThrownBy(() -> continuousIntegrationService.triggerBuild(participation)).isInstanceOf(GitLabCIException.class);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), never()).triggerPipeline(eq(repositoryPath), any(Trigger.class), anyString(), any());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testConfigureBuildPlanSuccess() throws Exception {
        continuousIntegrationTestService.testConfigureBuildPlan();
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
        final String repositoryUrl = "http://some.test.url/PROJECTNAME/REPONAME-exercise.git";
        final String repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl(repositoryUrl));
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);

        continuousIntegrationService.createBuildPlanForExercise(null, null, new VcsRepositoryUrl(repositoryUrl), null, null);

        verify(gitlab, atLeastOnce()).getProjectApi();
        verify(gitlab.getProjectApi(), atLeastOnce()).getProject(eq(repositoryPath));
        verify(gitlab.getProjectApi(), atLeastOnce()).updateProject(any(Project.class));
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
