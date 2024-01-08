package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingSubmissionConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

class GitlabServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @BeforeEach
    void initTestCase() {
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "student1")
    void testCheckIfProjectExistsFails() throws GitLabApiException {
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
    void testCheckIfGroupNotFoundThenProjectNotExists() {
        gitlabRequestMockProvider.mockGetOptionalGroup("project-key", Optional.empty());
        assertThat(versionControlService.checkIfProjectExists("project-key", "project name")).as("a project cannot exist if there is no group for it").isFalse();
    }

    @Test
    @WithMockUser(username = "student1")
    void testCheckIfGroupContainsNoRepositoriesThenProjectNotExists() throws GitLabApiException {
        final Group group = new Group();
        gitlabRequestMockProvider.mockGetOptionalGroup("project-key", Optional.of(group));
        gitlabRequestMockProvider.mockGetProjectsStream(group, Stream.empty());

        assertThat(versionControlService.checkIfProjectExists("project-key", "project name")).as("an empty exercise group can safely be reused").isFalse();
    }

    @Test
    @WithMockUser(username = "student1")
    void testCheckIfGroupContainsRepositoriesThenProjectExists() throws GitLabApiException {
        final Group group = new Group();
        gitlabRequestMockProvider.mockGetOptionalGroup("project-key", Optional.of(group));
        gitlabRequestMockProvider.mockGetProjectsStream(group, Stream.of(new Project()));

        assertThat(versionControlService.checkIfProjectExists("project-key", "project name")).as("a non-empty exercise group cannot be used for another exercise").isTrue();
    }

    @Test
    @WithMockUser(username = "student1")
    void testHealthOk() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("ok", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", gitlabServerUrl);
        assertThat(health.isUp()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1")
    void testHealthNotOk() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("notok", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", gitlabServerUrl).containsEntry("status", "notok");
        assertThat(health.isUp()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1")
    void testHealthException() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("ok", HttpStatus.INTERNAL_SERVER_ERROR);
        var health = versionControlService.health();
        assertThat(health.isUp()).isFalse();
        assertThat(health.getException()).isNotNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    void testGetDefaultBranch(String defaultBranch) throws URISyntaxException, GitLabApiException {
        VcsRepositoryUri repoUri = new VcsRepositoryUri("http://some.test.url/scm/PROJECTNAME/REPONAME-exercise.git");
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        String actualDefaultBranch = versionControlService.getDefaultBranchOfRepository(repoUri);
        assertThat(actualDefaultBranch).isEqualTo(defaultBranch);
    }

    @Test
    void testGetOrRetrieveDefaultBranch() throws GitLabApiException {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        ProgrammingExercise programmingExercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        programmingExercise.setBranch(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        versionControlService.getOrRetrieveBranchOfParticipation(programmingExercise.getSolutionParticipation());
        versionControlService.getOrRetrieveBranchOfParticipation(programmingExercise.getSolutionParticipation());

        verify(versionControlService).getDefaultBranchOfRepository(any());
    }

    @Test
    void testGetLastCommitDetails() throws JsonProcessingException {
        String latestCommitHash = "11028e4104243d8cbae9175f2bc938cb8c2d7924";
        Object body = new ObjectMapper().readValue(GITLAB_PUSH_EVENT_REQUEST, Object.class);
        Commit commit = versionControlService.getLastCommitDetails(body);
        assertThat(commit.commitHash()).isEqualTo(latestCommitHash);
        assertThat(commit.branch()).isNotNull();
        assertThat(commit.message()).isNotNull();
        assertThat(commit.authorEmail()).isNotNull();
        assertThat(commit.authorName()).isNotNull();
    }

    @Test
    void testGetLastCommitDetailsWrongCommitOrder() throws JsonProcessingException {
        String latestCommitHash = "11028e4104243d8cbae9175f2bc938cb8c2d7924";
        Object body = new ObjectMapper().readValue(GITLAB_PUSH_EVENT_REQUEST_WRONG_COMMIT_ORDER, Object.class);
        Commit commit = versionControlService.getLastCommitDetails(body);
        assertThat(commit.commitHash()).isEqualTo(latestCommitHash);
        assertThat(commit.branch()).isNotNull();
        assertThat(commit.message()).isNotNull();
        assertThat(commit.authorEmail()).isNotNull();
        assertThat(commit.authorName()).isNotNull();
    }

    @Test
    void testGetLastCommitDetailsWithoutCommits() throws JsonProcessingException {
        String latestCommitHash = "11028e4104243d8cbae9175f2bc938cb8c2d7924";
        Object body = new ObjectMapper().readValue(GITLAB_PUSH_EVENT_REQUEST_WITHOUT_COMMIT, Object.class);
        Commit commit = versionControlService.getLastCommitDetails(body);
        assertThat(commit.commitHash()).isEqualTo(latestCommitHash);
        assertThat(commit.branch()).isNull();
        assertThat(commit.message()).isNull();
        assertThat(commit.authorEmail()).isNull();
        assertThat(commit.authorName()).isNull();
    }
}
