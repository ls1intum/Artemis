package de.tum.cit.aet.artemis.jenkins.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.jenkins.exception.JenkinsException;
import de.tum.cit.aet.artemis.jenkins.service.jobs.JenkinsJobService;

/**
 * Unit tests for reading the config of a Jenkins folder without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class JenkinsJobServiceFolderConfigTest {

    @Mock
    private RestTemplate restTemplate;

    private JenkinsJobService jenkinsJobService;

    @BeforeEach
    void setUp() {
        jenkinsJobService = new JenkinsJobService(restTemplate);
        ReflectionTestUtils.setField(jenkinsJobService, "jenkinsServerUri", URI.create("http://jenkins.invalid"));
    }

    @Test
    void getFolderConfigReturnsNullIfTheFolderDoesNotExist() throws Exception {
        when(restTemplate.getForObject(any(URI.class), eq(JenkinsJobService.FolderJob.class))).thenReturn(null);

        assertThat(jenkinsJobService.getFolderConfig("FOLDER")).isNull();
    }

    @Test
    void getFolderConfigFailsIfJenkinsReturnsNoConfigBody() {
        when(restTemplate.getForObject(any(URI.class), eq(JenkinsJobService.FolderJob.class))).thenReturn(new JenkinsJobService.FolderJob("FOLDER", "description", "url"));
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(null);

        assertThatThrownBy(() -> jenkinsJobService.getFolderConfig("FOLDER")).isInstanceOf(JenkinsException.class).hasMessageContaining("empty config")
                .hasMessageContaining("FOLDER");
    }
}
