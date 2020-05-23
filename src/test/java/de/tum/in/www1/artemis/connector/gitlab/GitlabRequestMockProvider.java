package de.tum.in.www1.artemis.connector.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Visibility;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

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

    private final ObjectMapper mapper = new ObjectMapper();

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
        final var group = new Group().withPath(projectKey).withName(projectName).withVisibility(Visibility.PRIVATE);
        final var body = Map.of("key", projectKey, "name", projectName);

        mockServer.expect(ExpectedCount.once(), requestTo(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));

        mockGrantGroupPermissionToProject(exercise, ADMIN_GROUP_NAME, "PROJECT_ADMIN");
        mockGrantGroupPermissionToProject(exercise, exercise.getCourse().getInstructorGroupName(), "PROJECT_ADMIN");
        if (exercise.getCourse().getTeachingAssistantGroupName() != null) {
            mockGrantGroupPermissionToProject(exercise, exercise.getCourse().getTeachingAssistantGroupName(), "PROJECT_WRITE");
        }
    }

}
