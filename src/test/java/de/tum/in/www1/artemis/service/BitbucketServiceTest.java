package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

public class BitbucketServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @BeforeEach
    public void initTestCase() {
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        bitbucketRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthRunning() throws URISyntaxException, JsonProcessingException {
        bitbucketRequestMockProvider.mockHealth("RUNNING", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", bitbucketServerUrl);
        assertThat(health.isUp()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthNotRunning() throws URISyntaxException, JsonProcessingException {
        bitbucketRequestMockProvider.mockHealth("PAUSED", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", bitbucketServerUrl);
        assertThat(health.isUp()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthException() throws URISyntaxException, JsonProcessingException {
        bitbucketRequestMockProvider.mockHealth("RUNNING", HttpStatus.INTERNAL_SERVER_ERROR);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", bitbucketServerUrl);
        assertThat(health.isUp()).isFalse();
        assertThat(health.getException()).isNotNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    public void testGetDefaultBranch(String defaultBranch) throws IOException {
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, "PROJECTNAME");
        String actualDefaultBranch = versionControlService.getDefaultBranchOfRepository(new VcsRepositoryUrl("http://some.test.url/scm/PROJECTNAME/REPONAME-exercise.git"));
        assertThat(actualDefaultBranch).isEqualTo(defaultBranch);
    }
}
