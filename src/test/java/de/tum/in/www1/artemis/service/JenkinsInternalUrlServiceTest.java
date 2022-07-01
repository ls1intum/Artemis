package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsInternalUrlService;

class JenkinsInternalUrlServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private JenkinsInternalUrlService jenkinsInternalUrlService;

    private VcsRepositoryUrl vcsRepositoryUrl;

    private String ciUrl;

    private URL internalVcsUrl;

    private URL internalCiUrl;

    @BeforeEach
    void initTestCase() throws Exception {
        vcsRepositoryUrl = new VcsRepositoryUrl("http://localhost:80/some-repo.git");
        ciUrl = "http://localhost:8080/some-ci-path";
        internalVcsUrl = new URL("http://1.2.3.4:123");
        internalCiUrl = new URL("http://5.6.7.8:123");
        ReflectionTestUtils.setField(jenkinsInternalUrlService, "internalVcsUrl", Optional.empty());
        ReflectionTestUtils.setField(jenkinsInternalUrlService, "internalCiUrl", Optional.empty());
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetVcsUrlOnInternalVcsUrlEmpty() {
        var newVcsUrl = jenkinsInternalUrlService.toInternalVcsUrl(vcsRepositoryUrl);
        assertThat(newVcsUrl).hasToString(vcsRepositoryUrl.toString());
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetVcsUrlOnInternalVcsUrl() {
        ReflectionTestUtils.setField(jenkinsInternalUrlService, "internalVcsUrl", Optional.of(internalVcsUrl));

        var newVcsUrl = jenkinsInternalUrlService.toInternalVcsUrl(vcsRepositoryUrl);
        assertThat(newVcsUrl).hasToString("http://1.2.3.4:123/some-repo.git");

        var vcsRepositoryUrl = mock(VcsRepositoryUrl.class);
        doReturn(null).when(vcsRepositoryUrl).getURI();
        assertThat(jenkinsInternalUrlService.toInternalVcsUrl(vcsRepositoryUrl)).isEqualTo(vcsRepositoryUrl);

        String nullUrl = null;
        assertThat(jenkinsInternalUrlService.toInternalVcsUrl(nullUrl)).isNull();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetVcsUrlOnInternalVcsUrlMalformed() {
        var jenkinsInternalUrlServiceSpy = spy(jenkinsInternalUrlService);
        ReflectionTestUtils.setField(jenkinsInternalUrlServiceSpy, "internalVcsUrl", Optional.of(internalVcsUrl));

        when(jenkinsInternalUrlServiceSpy.replaceUrl(any(), any())).thenAnswer(invocation -> {
            throw new URISyntaxException("wrong input", "exception");
        });
        assertThat(jenkinsInternalUrlServiceSpy.toInternalVcsUrl(vcsRepositoryUrl)).isEqualTo(vcsRepositoryUrl);
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetCiUrlOnInternalCiUrlEmpty() {
        var newCiUrl = jenkinsInternalUrlService.toInternalCiUrl(ciUrl);
        assertThat(newCiUrl).hasToString(ciUrl);
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetCiUrlOnInternalCiUrl() {
        ReflectionTestUtils.setField(jenkinsInternalUrlService, "internalCiUrl", Optional.of(internalCiUrl));

        var newCiUrl = jenkinsInternalUrlService.toInternalCiUrl(ciUrl);
        assertThat(newCiUrl).hasToString("http://5.6.7.8:123/some-ci-path");

        assertThat(jenkinsInternalUrlService.toInternalCiUrl(null)).isNull();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetUrlOnInternalUrlWithoutPort() throws Exception {
        ReflectionTestUtils.setField(jenkinsInternalUrlService, "internalCiUrl", Optional.of(new URL("http://www.host.name.com/")));
        ReflectionTestUtils.setField(jenkinsInternalUrlService, "internalVcsUrl", Optional.of(new URL("http://www.hostname.com/")));

        var newVcsUrl = jenkinsInternalUrlService.toInternalVcsUrl(vcsRepositoryUrl);
        assertThat(newVcsUrl).hasToString("http://www.hostname.com/some-repo.git");

        var newCiUrl = jenkinsInternalUrlService.toInternalCiUrl(ciUrl);
        assertThat(newCiUrl).isEqualTo("http://www.host.name.com/some-ci-path");
    }
}
