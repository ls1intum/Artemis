package de.tum.in.www1.artemis.connector.gitlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabException;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabProjectDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@Component
@Profile("gitlab")
public class GitlabRequestMockProvider {

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.continuous-integration.user}")
    private String username;

    @Value("${artemis.continuous-integration.password}")
    private String password;

    private final RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();;

    private MockRestServiceServer mockServer;

    public GitlabRequestMockProvider(@Qualifier("gitlabRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectName = exercise.getProjectName();
        final var body = Map.of("path", projectKey, "name", projectName);

        mockServer.expect(ExpectedCount.once(), requestTo(GITLAB_SERVER_URL + "/api/v4/groups")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.CREATED));
        mockAddUserToGroup(exercise, "1");
    }

    private void mockAddUserToGroup(ProgrammingExercise exercise, String userID) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/4/projects").pathSegment(exercise.getProjectKey()).pathSegment("members")
            .queryParam("user_id", "1", "access_level", "50").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockCreateRepository(ProgrammingExercise exercise, String repositoryName) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var body = Map.of("name", repositoryName, "namespace_id", projectKey);
        final var createRepoPath = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/project");

        mockServer.expect(requestTo(createRepoPath.build().toUri())).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body)))
            .andRespond(withStatus(HttpStatus.CREATED));
    }

    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var templateRepoName = exercise.getProjectKey().toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        final var clonedRepoName = projectKey.toLowerCase() + "-" + username.toLowerCase();

        mockCopyRepository(projectKey, projectKey, templateRepoName, clonedRepoName);
    }

    public void mockCopyRepository(String sourceProjectKey, String targetProjectKey, String sourceRepoName, String targetRepoName)
        throws JsonProcessingException, URISyntaxException {
        final var originPath = sourceProjectKey + "%2F" + sourceRepoName.toLowerCase();
        final var targetRepoSlug = (targetProjectKey + "-" + targetRepoName).toLowerCase();

        final var copyRepoPath = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects")
            .pathSegment(originPath).pathSegment("fork").build().toUri();
        final var body = Map.of("namespace", targetProjectKey, "path", targetRepoSlug, "name", targetRepoSlug);

        mockServer.expect(requestTo(copyRepoPath)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body)))
            .andRespond(withStatus(HttpStatus.CREATED));
    }

    public void mockConfigureRepository(ProgrammingExercise exercise, String username, Set<User> users) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        for (User user : users) {
            mockGiveWritePermission(exercise, repoName, user.getLogin());
        }
        mockProtectBranches(exercise, repoName);
    }

    public void mockGiveWritePermission(ProgrammingExercise exercise, String repositoryName, String username) throws URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var permissionPath = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects")
            .pathSegment(projectPath).pathSegment("members")
            .pathSegment(username).queryParam("access_level", "30").build().toUri();

        mockServer.expect(requestTo(permissionPath)).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockProtectBranches(ProgrammingExercise exercise, String repositoryName) throws URISyntaxException {
        final var projectPath = exercise.getProjectKey() + "%2F" + repositoryName.toLowerCase();
        final var protectBranchPath = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects")
            .pathSegment(projectPath).pathSegment("protected_branches")
            .queryParam("name", "*", "push_access_level", "30", "merge_access_level", "30").build().toUri();
        mockServer.expect(requestTo(protectBranchPath)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRepositoryUrlIsValid(final URL repositoryUrl, final String projectKey, final boolean isValid) throws URISyntaxException {
        final var repositoryName = getRepositorySlugFromUrl(repositoryUrl);
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(projectPath).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(isValid ? HttpStatus.OK : HttpStatus.BAD_REQUEST));
    }

    /**
     * TODO: this method is currently copied from GitlabService for testing purposes. Think about how to properly reuse this method while allowing it to be mocked during testing
     *
     * Gets the repository slug from the given URL
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The repository slug
     */
    public String getRepositorySlugFromUrl(URL repositoryUrl) throws VersionControlException {
        final var splittedUrl = repositoryUrl.toString().split("/");
        if (splittedUrl[splittedUrl.length - 1].matches(".*\\.git")) {
            return splittedUrl[splittedUrl.length - 1].replace(".git", "");
        }

        throw new GitLabException("Repository URL is not a git URL! Can't get slug for " + repositoryUrl.toString());
    }

    public void mockCheckIfProjectExists(final ProgrammingExercise exercise, final boolean exists) throws JsonProcessingException, URISyntaxException {
        final var existsUri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(exercise.getProjectKey()).build().toUri();
        exercise.getProjectName();
        final var uniqueUri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").queryParam("name", exercise.getProjectName()).build()
            .toUri();
        final var foundProject = new GitLabProjectDTO();
        foundProject.setName(exercise.getProjectName() + (exists ? "" : "abc"));

        mockServer.expect(requestTo(existsUri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo(uniqueUri)).andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(foundProject)));
    }

    public void mockGetExistingWebhooks(String projectKey, String repositoryName) throws URISyntaxException {
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(projectPath).pathSegment("hooks").build().toUri();
        final var existingHooks = "{\"values\": []}";

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK).body(existingHooks).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockAddWebhook(String projectKey, String repositoryName, String url) throws JsonProcessingException, URISyntaxException {
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(projectPath).pathSegment("hooks").build().toUri();
        final var body = Map.of("id", "1", "url", url, "push_events", true);

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockAddWebHooks(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment("hooks").build().toUri();
        mockServer.expect(ExpectedCount.once(), requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockDeleteProject(String projectKey) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/groups").pathSegment(String.valueOf(projectKey)).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockDeleteRepository(String projectKey, String repositoryName) throws URISyntaxException {
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(projectPath).build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockSetRepositoryPermissionsToReadOnly(String repositorySlug, String projectKey, Set<User> users) throws URISyntaxException {
        Map<String, String> body = new java.util.HashMap<>();
        User[] userArray = new User[users.size()];
        users.toArray(userArray);
        for (int i = 0; i < users.size(); i++) {
            body.put(userArray[i].getName(), String.valueOf(i+1));
        }
        for (User user : users) {
            mockSetStudentRepositoryPermission(repositorySlug, projectKey, body.get(user.getName()));
        }
    }

    private void mockSetStudentRepositoryPermission(String repositoryName, String projectKey, String userID) throws URISyntaxException {
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/4/projects").pathSegment(projectPath).pathSegment("members")
            .queryParam("user_id", "1", "access_level", "10").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRemoveStudentRepositoryAccess(final String repositoryName, final String projectKey, final User user) throws URISyntaxException {
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var body = Map.of("id", 1, "name", user.getName());
        final var userID = body.get("id");
        final var uri = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/4/projects").pathSegment(projectPath)
            .pathSegment("members").pathSegment(String.valueOf(userID)).build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }

}
