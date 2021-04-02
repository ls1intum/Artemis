package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.net.URL;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;
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
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(gitlabServerUrl);
        assertThat(health.isUp()).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthNotOk() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("notok", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(gitlabServerUrl);
        assertThat(health.getAdditionalInfo().get("status")).isEqualTo("notok");
        assertThat(health.isUp()).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthException() throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth("ok", HttpStatus.INTERNAL_SERVER_ERROR);
        var health = versionControlService.health();
        assertThat(health.isUp()).isEqualTo(false);
        assertThat(health.getException()).isNotNull();
    }
}
