package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URISyntaxException;
import java.net.URL;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.VersionControlException;

public class GitlabServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

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
    @WithMockUser(username = "student1")
    public void testCheckIfProjectExistsFails() throws GitLabApiException {
        gitlabRequestMockProvider.mockFailToCheckIfProjectExists("project-key");
        try {
            versionControlService.checkIfProjectExists("project-key", "project-name");
        }
        catch (VersionControlException e) {
            assertThat(e.getMessage()).isNotEmpty();
        }
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthOk() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("ok", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", gitlabServerUrl);
        assertThat(health.isUp()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthNotOk() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("notok", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", gitlabServerUrl).containsEntry("status", "notok");
        assertThat(health.isUp()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthException() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("ok", HttpStatus.INTERNAL_SERVER_ERROR);
        var health = versionControlService.health();
        assertThat(health.isUp()).isFalse();
        assertThat(health.getException()).isNotNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    public void testGetDefaultBranch(String defaultBranch) throws URISyntaxException, GitLabApiException {
        VcsRepositoryUrl repoURL = new VcsRepositoryUrl("http://some.test.url/scm/PROJECTNAME/REPONAME-exercise.git");
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        String actualDefaultBranch = versionControlService.getDefaultBranchOfRepository(repoURL);
        assertThat(actualDefaultBranch).isEqualTo(defaultBranch);
    }

    @Test
    public void testGetOrRetrieveDefaultBranch() throws GitLabApiException {
        Course course = database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        versionControlService.getOrRetrieveBranchOfParticipation(programmingExercise.getSolutionParticipation());
        versionControlService.getOrRetrieveBranchOfParticipation(programmingExercise.getSolutionParticipation());

        verify(versionControlService, times(1)).getDefaultBranchOfRepository(any());
    }
}
