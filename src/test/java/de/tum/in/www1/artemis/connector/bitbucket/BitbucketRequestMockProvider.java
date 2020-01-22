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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketBranchProtectionDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketCloneDTO;
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
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCheckIfProjectExists(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectName = exercise.getProjectName();
        final var bitbucketSearchDTO = new BitbucketSearchDTO();
        bitbucketSearchDTO.setSize(0);
        bitbucketSearchDTO.setSearchResults(new ArrayList<>());

        mockServer.expect(ExpectedCount.once(), requestTo(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        final var projectSearchPath = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects").queryParam("name", projectName);
        mockServer.expect(ExpectedCount.once(), requestTo(projectSearchPath.build().toUri())).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).body(mapper.writeValueAsString(bitbucketSearchDTO)).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectName = exercise.getProjectName();
        final var body = Map.of("key", projectKey, "name", projectName);

        mockServer.expect(ExpectedCount.once(), requestTo(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));

        mockGrantGroupPermissionToProject(exercise, ADMIN_GROUP_NAME, "PROJECT_ADMIN");
        mockGrantGroupPermissionToProject(exercise, exercise.getCourse().getInstructorGroupName(), "PROJECT_ADMIN");
        if (exercise.getCourse().getTeachingAssistantGroupName() != null) {
            mockGrantGroupPermissionToProject(exercise, exercise.getCourse().getTeachingAssistantGroupName(), "PROJECT_WRITE");
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
        final var searchResult = new BitbucketSearchDTO();
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
        final var copyRepoPath = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI()).path("/rest/api/1.0/projects/").pathSegment(projectKey).path("/repos/")
                .pathSegment(templateRepoName).build().toUri();
        final var cloneBody = new BitbucketCloneDTO(clonedRepoName, new BitbucketCloneDTO.CloneDetailsDTO(projectKey));

        mockServer.expect(requestTo(copyRepoPath)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(cloneBody)))
                .andRespond(withStatus(HttpStatus.CREATED));
    }

    public void mockConfigureRepository(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        mockGiveWritePermission(exercise, repoName, username);
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
}
