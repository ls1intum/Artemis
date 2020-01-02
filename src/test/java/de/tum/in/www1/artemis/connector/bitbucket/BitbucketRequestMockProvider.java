package de.tum.in.www1.artemis.connector.bitbucket;

import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
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

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketSearchDTO;

@Component
@Profile("bitbucket")
public class BitbucketRequestMockProvider {

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER_URL;

    @Value("${artemis.jira.admin-group-name}")
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
        mockGrantGroupPermissionToProject(exercise, exercise.getCourse().getTeachingAssistantGroupName(), "PROJECT_WRITE");
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
}
