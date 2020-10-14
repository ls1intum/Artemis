package de.tum.in.www1.artemis.connector.bitbucket;

import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketBranchProtectionDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketCloneDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketProjectDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketSearchDTO;

@Component
@Profile("bitbucket")
public class BitbucketRequestMockProvider {

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER_URL;

    @Value("${artemis.user-management.external.admin-group-name}")
    private String ADMIN_GROUP_NAME;

    private final RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private MockRestServiceServer mockServer;

    public BitbucketRequestMockProvider(@Qualifier("bitbucketRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        enableMockingOfRequests(false);
    }

    public void enableMockingOfRequests(boolean ignoreExpectOrder) {
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(ignoreExpectOrder);
        mockServer = builder.build();
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectName = exercise.getProjectName();
        final var body = Map.of("key", projectKey, "name", projectName);

        mockServer.expect(ExpectedCount.once(), requestTo(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));

        mockGrantGroupPermissionToProject(exercise, ADMIN_GROUP_NAME, "PROJECT_ADMIN");
        mockGrantGroupPermissionToProject(exercise, exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName(), "PROJECT_ADMIN");
        if (exercise.getCourseViaExerciseGroupOrCourseMember().getTeachingAssistantGroupName() != null) {
            mockGrantGroupPermissionToProject(exercise, exercise.getCourseViaExerciseGroupOrCourseMember().getTeachingAssistantGroupName(), "PROJECT_WRITE");
        }
    }

    public void mockGrantGroupPermissionToProject(ProgrammingExercise exercise, String groupName, String permission) throws URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var permissionPath = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(projectKey).path("/permissions/groups/")
                .queryParam("name", groupName).queryParam("permission", permission);

        mockServer.expect(ExpectedCount.once(), requestTo(permissionPath.build().toUri())).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockCreateRepository(ProgrammingExercise exercise, String repositoryName) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var body = Map.of("name", repositoryName);
        final var createRepoPath = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(projectKey).path("/repos");

        mockServer.expect(requestTo(createRepoPath.build().toUri())).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body)))
                .andRespond(withStatus(HttpStatus.OK));
    }

    public void mockAddWebHooks(ProgrammingExercise exercise) throws IOException {
        final var projectKey = exercise.getProjectKey();
        final var searchResult = new BitbucketSearchDTO<BitbucketProjectDTO>();
        searchResult.setSize(0);
        searchResult.setSearchResults(new ArrayList<>());

        mockServer.expect(ExpectedCount.manyTimes(), requestTo(matchesPattern(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/.*/webhooks")))
                .andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK).body(mapper.writeValueAsString(searchResult)).contentType(MediaType.APPLICATION_JSON));
        mockServer.expect(ExpectedCount.manyTimes(), requestTo(matchesPattern(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/.*/webhooks")))
                .andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var templateRepoName = exercise.getProjectKey().toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        final var clonedRepoName = projectKey.toLowerCase() + "-" + username.toLowerCase();

        mockCopyRepository(projectKey, projectKey, templateRepoName, clonedRepoName);
    }

    public void mockCopyRepository(String sourceProjectKey, String targetProjectKey, String sourceRepoName, String targetRepoName)
            throws JsonProcessingException, URISyntaxException {
        sourceRepoName = sourceRepoName.toLowerCase();
        targetRepoName = targetRepoName.toLowerCase();
        final var copyRepoPath = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(sourceProjectKey).path("/repos/")
                .pathSegment(sourceRepoName).build().toUri();
        final var cloneBody = new BitbucketCloneDTO(targetRepoName, new BitbucketCloneDTO.CloneDetailsDTO(targetProjectKey));

        mockServer.expect(requestTo(copyRepoPath)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(cloneBody)))
                .andRespond(withStatus(HttpStatus.CREATED));
    }

    public void mockConfigureRepository(ProgrammingExercise exercise, String username, Set<User> users) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        for (User user : users) {
            if (exercise.hasCourse()) {
                mockGiveWritePermission(exercise, repoName, user.getLogin());
            }
            // exam exercises receive write permissions when the exam starts
        }
        mockProtectBranches(exercise, repoName);
    }

    public void mockGiveWritePermission(ProgrammingExercise exercise, String repositoryName, String username) throws URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var permissionPath = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(projectKey).path("/repos/")
                .pathSegment(repositoryName).path("/permissions/users").queryParam("name", username).queryParam("permission", "REPO_WRITE").build().toUri();

        mockServer.expect(requestTo(permissionPath)).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockProtectBranches(ProgrammingExercise exercise, String repositoryName) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var type = new BitbucketBranchProtectionDTO.TypeDTO("PATTERN", "Pattern");
        // A wildcard (*) ist used to protect all branches
        final var matcher = new BitbucketBranchProtectionDTO.MatcherDTO("*", "*", type, true);
        // Prevent force-pushes
        final var fastForwardOnlyProtection = new BitbucketBranchProtectionDTO("fast-forward-only", matcher);
        // Prevent deletion of branches
        final var noDeletesProtection = new BitbucketBranchProtectionDTO("no-deletes", matcher);
        final var body = List.of(fastForwardOnlyProtection, noDeletesProtection);
        final var protectBranchPath = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/branch-permissions/2.0/projects/").pathSegment(projectKey)
                .path("/repos/").pathSegment(repositoryName).path("/restrictions").build().toUri();

        mockServer.expect(requestTo(protectBranchPath)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body)))
                .andExpect(content().contentType("application/vnd.atl.bitbucket.bulk+json")).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRepositoryUrlIsValid(final URL repositoryUrl, final String projectKey, final boolean isValid) throws URISyntaxException {
        final var repositoryName = getRepositorySlugFromUrl(repositoryUrl);
        final var uri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(projectKey).pathSegment("repos")
                .pathSegment(repositoryName).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(isValid ? HttpStatus.OK : HttpStatus.BAD_REQUEST));
    }

    /**
     * TODO: this method is currently copied from BambooService for testing purposes. Think about how to properly reuse this method while allowing it to be mocked during testing
     *
     * Gets the repository slug from the given URL
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The repository slug
     */
    public String getRepositorySlugFromUrl(URL repositoryUrl) {
        // https://ga42xab@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String[] urlParts = repositoryUrl.getFile().split("/");
        String repositorySlug = urlParts[urlParts.length - 1];
        if (repositorySlug.endsWith(".git")) {
            repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
        }
        else {
            throw new IllegalArgumentException("No repository slug could be found");
        }

        return repositorySlug;
    }

    /**
     * This method mocks that the programming exercise with the same project key (based on the course + programming exercise short name) already exists
     *
     * @param exercise the programming exercise that already exists
     */
    public void mockProjectKeyExists(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var existsUri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(exercise.getProjectKey()).build().toUri();
        mockServer.expect(requestTo(existsUri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK));
    }

    /**
     * This method mocks that the programming exercise with the same project name already exists (depending on the boolean input exists), based on the programming exercise title
     *
     * @param exercise the programming exercise that might already exist
     * @param exists whether the programming exercise with the same title exists
     * @throws JsonProcessingException
     * @throws URISyntaxException
     */
    public void mockCheckIfProjectExists(final ProgrammingExercise exercise, final boolean exists) throws JsonProcessingException, URISyntaxException {
        final var existsUri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(exercise.getProjectKey()).build().toUri();
        final var uniqueUri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").queryParam("name", exercise.getProjectName()).build()
                .toUri();
        final var searchResults = new BitbucketSearchDTO<BitbucketProjectDTO>();
        final var foundProject = new BitbucketProjectDTO();
        foundProject.setName(exercise.getProjectName() + (exists ? "" : "abc"));
        searchResults.setSearchResults(List.of(foundProject));
        searchResults.setSize(1);

        mockServer.expect(requestTo(existsUri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo(uniqueUri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(searchResults)));
    }

    public void mockGetExistingWebhooks(String projectKey, String repositoryName) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").pathSegment(projectKey).path("repos").pathSegment(repositoryName)
                .path("webhooks").build().toUri();
        final var existingHooks = "{\"values\": []}";

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK).body(existingHooks).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockAddWebhook(String projectKey, String repositoryName, String url) throws JsonProcessingException, URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").pathSegment(projectKey).path("repos").pathSegment(repositoryName)
                .path("webhooks").build().toUri();
        final var body = Map.of("name", "Artemis WebHook", "url", url, "events", List.of("repo:refs_changed"));

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockDeleteProject(String projectKey) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").pathSegment(projectKey).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockDeleteRepository(String projectKey, String repositoryName) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").pathSegment(projectKey).path("repos").pathSegment(repositoryName)
                .build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockSetRepositoryPermissionsToReadOnly(String repositorySlug, String projectKey, Set<User> users) throws URISyntaxException {
        for (User user : users) {
            mockSetStudentRepositoryPermission(repositorySlug, projectKey, user.getLogin());
        }
    }

    private void mockSetStudentRepositoryPermission(String repositorySlug, String projectKey, String username) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").pathSegment(projectKey).path("repos").pathSegment(repositorySlug)
                .path("permissions/users").queryParam("name", username).queryParam("permission", "REPO_READ").build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRemoveMemberFromRepository(String repositorySlug, String projectKey, User user) throws URISyntaxException {
        mockRemoveStudentRepositoryAccess(repositorySlug, projectKey, user.getLogin());
    }

    private void mockRemoveStudentRepositoryAccess(String repositorySlug, String projectKey, String username) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").pathSegment(projectKey).path("repos").pathSegment(repositorySlug)
                .path("permissions/users").queryParam("name", username).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }
}
