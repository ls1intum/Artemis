package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationGitLabCIGitLabTest;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.GitLabCIException;

public class GitLabCIServiceTest extends AbstractSpringIntegrationGitLabCIGitLabTest {

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @BeforeEach
    public void initTestCase() {
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        gitlabRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testHealthOk() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("ok", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", gitlabServerUrl);
        assertThat(health.isUp()).isTrue();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testHealthNotOk() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("notok", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", gitlabServerUrl).containsEntry("status", "notok");
        assertThat(health.isUp()).isFalse();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testHealthException() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("ok", HttpStatus.INTERNAL_SERVER_ERROR);
        var health = versionControlService.health();
        assertThat(health.isUp()).isFalse();
        assertThat(health.getException()).isNotNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testAddFeedbackToResult() {
        var result = continuousIntegrationService.getWebHookUrl(null, null);
        assertThat(result).isEqualTo(Optional.empty());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testCheckIfProjectExists() {
        var result = continuousIntegrationService.checkIfProjectExists(null, null);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testRetrieveLatestArtifact() {
        var result = continuousIntegrationService.retrieveLatestArtifact(null);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testGetLatestBuildLogs() {
        var result = continuousIntegrationService.getLatestBuildLogs(null);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testCheckIfBuildPlanExists() {
        var result = continuousIntegrationService.checkIfBuildPlanExists(null, null);
        assertThat(result).isFalse();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testGetBuildStatus() {
        var result = continuousIntegrationService.getBuildStatus(null);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testConvertBuildResult() {
        var result = continuousIntegrationService.convertBuildResult(null);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testGetPlanKey() {
        var result = continuousIntegrationService.getPlanKey(null);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testTriggerBuildSuccess() throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateTrigger(false);
        gitlabRequestMockProvider.mockTriggerPipeline(false);
        gitlabRequestMockProvider.mockDeleteTrigger(false);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl("http://some.test.url/scm/PROJECTNAME/REPONAME-exercise.git");
        continuousIntegrationService.triggerBuild(participation);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testTriggerBuildFails() throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateTrigger(true);
        gitlabRequestMockProvider.mockTriggerPipeline(true);
        gitlabRequestMockProvider.mockDeleteTrigger(true);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl("http://some.test.url/scm/PROJECTNAME/REPONAME-exercise.git");
        assertThatThrownBy(() -> continuousIntegrationService.triggerBuild(participation)).isInstanceOf(GitLabCIException.class);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testConfigureBuildPlanSuccess() throws GitLabApiException {
        gitlabRequestMockProvider.mockGetProject(false);
        gitlabRequestMockProvider.mockUpdateProject(false);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl("http://some.test.url/scm/PROJECTNAME/REPONAME-exercise.git");
        continuousIntegrationService.configureBuildPlan(participation, "main");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    public void testConfigureBuildPlanFails() throws GitLabApiException {
        gitlabRequestMockProvider.mockGetProject(true);
        gitlabRequestMockProvider.mockUpdateProject(true);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl("http://some.test.url/scm/PROJECTNAME/REPONAME-exercise.git");
        assertThatThrownBy(() -> continuousIntegrationService.configureBuildPlan(participation, "main")).isInstanceOf(GitLabCIException.class);
    }
}
