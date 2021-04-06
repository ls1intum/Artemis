package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

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
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(bitbucketServerUrl);
        assertThat(health.isUp()).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthNotRunning() throws URISyntaxException, JsonProcessingException {
        bitbucketRequestMockProvider.mockHealth("PAUSED", HttpStatus.OK);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(bitbucketServerUrl);
        assertThat(health.isUp()).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthException() throws URISyntaxException, JsonProcessingException {
        bitbucketRequestMockProvider.mockHealth("RUNNING", HttpStatus.INTERNAL_SERVER_ERROR);
        var health = versionControlService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(bitbucketServerUrl);
        assertThat(health.isUp()).isEqualTo(false);
        assertThat(health.getException()).isNotNull();
    }
}
