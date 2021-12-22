package de.tum.in.www1.artemis.connector;

import static de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketPermission.*;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketPermission;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.*;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Component
@Profile("bitbucket")
public class BitbucketRequestMockProvider {

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @Value("${artemis.user-management.external.admin-group-name}")
    private String adminGroupName;

    @Value("${artemis.lti.user-prefix-edx:#{null}}")
    private Optional<String> userPrefixEdx;

    @Value("${artemis.lti.user-prefix-u4i:#{null}}")
    private Optional<String> userPrefixU4I;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UrlService urlService;

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private MockRestServiceServer mockServer;

    private MockRestServiceServer mockServerShortTimeout;

    public BitbucketRequestMockProvider(@Qualifier("bitbucketRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutBitbucketRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    public void enableMockingOfRequests() {
        enableMockingOfRequests(false);
    }

    public void enableMockingOfRequests(boolean ignoreExpectOrder) {
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(ignoreExpectOrder);
        mockServer = builder.build();

        MockRestServiceServer.MockRestServiceServerBuilder builderShortTimeout = MockRestServiceServer.bindTo(shortTimeoutRestTemplate);
        builderShortTimeout.ignoreExpectOrder(ignoreExpectOrder);
        mockServerShortTimeout = builderShortTimeout.build();
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectName = exercise.getProjectName();
        final var body = new BitbucketProjectDTO(projectKey, projectName);

        mockServer.expect(ExpectedCount.once(), requestTo(bitbucketServerUrl + "/rest/api/latest/projects")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));

        mockGrantGroupPermissionToProject(exercise, adminGroupName, PROJECT_ADMIN);
        mockGrantGroupPermissionToProject(exercise, exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName(), PROJECT_ADMIN);
        if (exercise.getCourseViaExerciseGroupOrCourseMember().getEditorGroupName() != null) {
            mockGrantGroupPermissionToProject(exercise, exercise.getCourseViaExerciseGroupOrCourseMember().getEditorGroupName(), PROJECT_WRITE);
        }
        if (exercise.getCourseViaExerciseGroupOrCourseMember().getTeachingAssistantGroupName() != null) {
            mockGrantGroupPermissionToProject(exercise, exercise.getCourseViaExerciseGroupOrCourseMember().getTeachingAssistantGroupName(), PROJECT_READ);
        }
    }

    public void mockGrantGroupPermissionToProject(ProgrammingExercise exercise, String groupName, BitbucketPermission permission) throws URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var permissionPath = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").pathSegment(projectKey).path("/permissions/groups/")
                .queryParam("name", groupName).queryParam("permission", permission);

        mockServer.expect(ExpectedCount.once(), requestTo(permissionPath.build().toUri())).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockCreateRepository(ProgrammingExercise exercise, String repositoryName) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var body = new BitbucketRepositoryDTO(repositoryName);
        final var createRepoPath = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").pathSegment(projectKey).path("/repos");

        mockServer.expect(requestTo(createRepoPath.build().toUri())).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body)))
                .andRespond(withStatus(HttpStatus.OK));
    }

    public void mockAddWebHooks(ProgrammingExercise exercise) throws IOException {
        final var projectKey = exercise.getProjectKey();
        final var searchResult = new BitbucketSearchDTO<BitbucketWebHookDTO>();
        searchResult.setSize(0);
        searchResult.setSearchResults(new ArrayList<>());

        mockServer.expect(ExpectedCount.manyTimes(), requestTo(matchesPattern(bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/.*/webhooks")))
                .andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK).body(mapper.writeValueAsString(searchResult)).contentType(MediaType.APPLICATION_JSON));
        mockServer.expect(ExpectedCount.manyTimes(), requestTo(matchesPattern(bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/.*/webhooks")))
                .andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var clonedRepoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        mockCreateRepository(exercise, clonedRepoName);
    }

    public void mockGetBitbucketRepository(ProgrammingExercise exercise, String bitbucketRepoName, BitbucketRepositoryDTO bitbucketRepository)
            throws URISyntaxException, JsonProcessingException {
        URI uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").pathSegment(exercise.getProjectKey()).pathSegment("repos")
                .pathSegment(bitbucketRepoName).build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(bitbucketRepository)));
    }

    public void mockConfigureRepository(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        for (User user : users) {
            // add mock for userExists() check, if the username contains edx_ or u4i_
            String loginName = user.getLogin();
            if (userPrefixEdx.isPresent() && loginName.startsWith(userPrefixEdx.get()) || userPrefixU4I.isPresent() && loginName.startsWith(userPrefixU4I.get())) {
                if (ltiUserExists) {
                    mockUserExists(loginName);
                }
                else {
                    mockUserDoesNotExist(loginName);
                    String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
                    mockCreateUser(loginName, passwordService.decryptPassword(user.getPassword()), user.getEmail(), displayName);
                    mockAddUserToGroups();
                }
            }
            mockGiveWritePermission(exercise, repoName, user.getLogin(), HttpStatus.OK);
        }
        mockProtectBranches(exercise, repoName);
    }

    public void mockUserExists(String userName) throws URISyntaxException {
        final var path = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/users/").path(userName).build().toUri();
        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockUserDoesNotExist(String userName) throws URISyntaxException {
        final var path = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/users/").path(userName).build().toUri();
        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
    }

    public void mockCreateUser(String userName, String password, String emailAddress, String displayName) {
        final var path = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users").queryParam("name", userName).queryParam("email", emailAddress)
                .queryParam("emailAddress", emailAddress).queryParam("password", password).queryParam("displayName", displayName).queryParam("addToDefaultGroup", "true")
                .queryParam("notify", "false").build().toUri();
        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockAddUserToGroups() throws URISyntaxException {
        final var path = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/admin/users/add-groups").build().toUri();
        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockGiveWritePermission(ProgrammingExercise exercise, String repositoryName, String username, HttpStatus status) throws URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var permissionPath = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").pathSegment(projectKey).path("/repos/")
                .pathSegment(repositoryName).path("/permissions/users").queryParam("name", username).queryParam("permission", "REPO_WRITE").build().toUri();

        mockServer.expect(requestTo(permissionPath)).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(status));
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
        final var protectBranchPath = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/branch-permissions/2.0/projects/").pathSegment(projectKey)
                .path("/repos/").pathSegment(repositoryName).path("/restrictions").build().toUri();

        mockServer.expect(requestTo(protectBranchPath)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body)))
                .andExpect(content().contentType("application/vnd.atl.bitbucket.bulk+json")).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRepositoryUrlIsValid(final VcsRepositoryUrl repositoryUrl, final String projectKey, final boolean isValid) throws URISyntaxException {
        final var repositoryName = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").pathSegment(projectKey).pathSegment("repos")
                .pathSegment(repositoryName).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(isValid ? HttpStatus.OK : HttpStatus.BAD_REQUEST));
    }

    /**
     * This method mocks that the programming exercise with the same project key (based on the course + programming exercise short name) already exists
     *
     * @param exercise the programming exercise that already exists
     */
    public void mockProjectKeyExists(ProgrammingExercise exercise) throws URISyntaxException, JsonProcessingException {
        final var existsUri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").pathSegment(exercise.getProjectKey()).build().toUri();
        var existingProject = new BitbucketProjectDTO(exercise.getProjectKey());
        existingProject.setName("existingProject");
        mockServer.expect(requestTo(existsUri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(existingProject)));
    }

    /**
     * This method mocks that the programming exercise with the same project name already exists (depending on the boolean input exists), based on the programming exercise title
     *
     * @param exercise the programming exercise that might already exist
     * @param exists whether the programming exercise with the same title exists
     * @throws JsonProcessingException exception in the processing of json files
     * @throws URISyntaxException exception in the processing of uris
     */
    public void mockCheckIfProjectExists(final ProgrammingExercise exercise, final boolean exists) throws JsonProcessingException, URISyntaxException {
        final var existsUri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").pathSegment(exercise.getProjectKey()).build().toUri();
        final var uniqueUri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects").queryParam("name", exercise.getProjectName()).build()
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

    public void mockGetExistingWebhooks(String projectKey, String repositoryName) throws URISyntaxException, JsonProcessingException {
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects").pathSegment(projectKey).path("repos").pathSegment(repositoryName)
                .path("webhooks").build().toUri();
        final var searchResult = new BitbucketSearchDTO<BitbucketWebHookDTO>();
        searchResult.setSize(0);
        searchResult.setSearchResults(new ArrayList<>());

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).body(mapper.writeValueAsString(searchResult)).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockAddWebhook(String projectKey, String repositoryName, String url) throws JsonProcessingException, URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects").pathSegment(projectKey).path("repos").pathSegment(repositoryName)
                .path("webhooks").build().toUri();
        final var body = new BitbucketWebHookDTO("Artemis WebHook", url, List.of("repo:refs_changed"));
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockDeleteProject(String projectKey, boolean shouldFail) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects").pathSegment(projectKey).build().toUri();
        var status = shouldFail ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(status));
    }

    public void mockDeleteRepository(String projectKey, String repositoryName, boolean shouldFail) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects").pathSegment(projectKey).path("repos").pathSegment(repositoryName)
                .build().toUri();
        var status = shouldFail ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(status));
    }

    public void mockDefaultBranch(String defaultBranch, String projectKey) throws BitbucketException, IOException {
        mockGetDefaultBranch(defaultBranch, projectKey);
        mockPutDefaultBranch(projectKey);
    }

    public void mockDefaultBranch(String defaultBranch, VcsRepositoryUrl repoURL) throws BitbucketException, IOException {
        String projectKey = urlService.getProjectKeyFromRepositoryUrl(repoURL);
        mockGetDefaultBranch(defaultBranch, projectKey);
        mockPutDefaultBranch(projectKey);
    }

    private void mockGetDefaultBranch(String defaultBranch, String projectKey) throws BitbucketException, IOException {
        var mockResponse = new BitbucketDefaultBranchDTO("refs/heads/" + defaultBranch);
        mockResponse.setDisplayId(defaultBranch);
        var getDefaultBranchPattern = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/.*/default-branch";

        mockServer.expect(ExpectedCount.manyTimes(), requestTo(matchesPattern(getDefaultBranchPattern))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).body(mapper.writeValueAsString(mockResponse)).contentType(MediaType.APPLICATION_JSON));
    }

    private void mockPutDefaultBranch(String projectKey) throws BitbucketException {
        var getDefaultBranchPattern = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/.*/branches/default";

        mockServer.expect(ExpectedCount.manyTimes(), requestTo(matchesPattern(getDefaultBranchPattern))).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockSetRepositoryPermissionsToReadOnly(String repositorySlug, String projectKey, Set<User> users) throws URISyntaxException {
        for (User user : users) {
            mockSetStudentRepositoryPermission(repositorySlug, projectKey, user.getLogin());
        }
    }

    private void mockSetStudentRepositoryPermission(String repositorySlug, String projectKey, String username) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects").pathSegment(projectKey).path("repos").pathSegment(repositorySlug)
                .path("permissions/users").queryParam("name", username).queryParam("permission", "REPO_READ").build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRemoveMemberFromRepository(String repositorySlug, String projectKey, User user) throws URISyntaxException {
        mockRemoveStudentRepositoryAccess(repositorySlug, projectKey, user.getLogin());
    }

    private void mockRemoveStudentRepositoryAccess(String repositorySlug, String projectKey, String username) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects").pathSegment(projectKey).path("repos").pathSegment(repositorySlug)
                .path("permissions/users").queryParam("name", username).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockHealth(String state, HttpStatus httpStatus) throws URISyntaxException, JsonProcessingException {
        var response = Map.of("state", state);
        var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/status").build().toUri();
        mockServerShortTimeout.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(httpStatus).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) throws URISyntaxException, JsonProcessingException {
        String json = "{ \"message\" : \"Merge branch 'develop' into master\", \"author\": { \"name\" : \"admin\", \"emailAddress\" : \"admin@bitbucket.de\" } } ";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(json);
        final var uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/1.0/projects").pathSegment(projectKey, "repos", repositorySlug, "commits", hash)
                .build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    public void mockGetBitbucketRepository(String projectKey, String repositorySlug) throws URISyntaxException, JsonProcessingException {
        BitbucketRepositoryDTO mockResponse = new BitbucketRepositoryDTO("asd", repositorySlug, projectKey, "ssh:cloneUrl");
        String body = mapper.writeValueAsString(mockResponse);
        URI uri = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI()).path("/rest/api/latest/projects/").path(projectKey).path("/repos/").path(repositorySlug).build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(body).contentType(MediaType.APPLICATION_JSON));
    }

    public static String repositorySlugOf(ProgrammingExerciseStudentParticipation participation) {
        return (participation.getProgrammingExercise().getProjectKey() + "-" + participation.getParticipantIdentifier()).toLowerCase();
    }
}
