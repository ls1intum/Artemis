package de.tum.cit.aet.artemis.jenkins.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.jenkins.exception.JenkinsException;
import de.tum.cit.aet.artemis.jenkins.service.jobs.JenkinsJobService;

/**
 * Unit tests for reading the config of a Jenkins folder without a Spring context.
 */
class JenkinsJobServiceFolderConfigTest {

    private static final String JENKINS_URL = "http://jenkins.invalid";

    private MockRestServiceServer server;

    private JenkinsJobService jenkinsJobService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        jenkinsJobService = new JenkinsJobService(builder.build());
        ReflectionTestUtils.setField(jenkinsJobService, "jenkinsServerUri", URI.create(JENKINS_URL));
    }

    @Test
    void getFolderConfigReturnsNullIfTheFolderDoesNotExist() throws Exception {
        server.expect(requestTo(startsWith(JENKINS_URL))).andRespond(withSuccess());

        assertThat(jenkinsJobService.getFolderConfig("FOLDER")).isNull();
        server.verify();
    }

    @Test
    void getFolderConfigFailsIfJenkinsReturnsNoConfigBody() {
        // The first request reads the folder, the second one its config, which comes back without a body.
        server.expect(requestTo(startsWith(JENKINS_URL)))
                .andRespond(withSuccess("{\"name\":\"FOLDER\",\"description\":\"description\",\"url\":\"url\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(JENKINS_URL))).andRespond(withSuccess());

        assertThatThrownBy(() -> jenkinsJobService.getFolderConfig("FOLDER")).isInstanceOf(JenkinsException.class).hasMessageContaining("empty config")
                .hasMessageContaining("FOLDER");
    }
}
