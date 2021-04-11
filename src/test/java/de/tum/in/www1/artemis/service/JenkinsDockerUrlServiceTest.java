package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsDockerUrlService;

public class JenkinsDockerUrlServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    @SpyBean
    private JenkinsDockerUrlService jenkinsDockerUrlService;

    private VcsRepositoryUrl vcsRepositoryUrl;

    private String ciUrl;

    private URL dockerVcsUrl;

    private URL dockerCiUrl;

    @BeforeEach
    public void initTestCase() throws Exception {
        vcsRepositoryUrl = new VcsRepositoryUrl("http://localhost:80/some-repo.git");
        ciUrl = "http://localhost:8080/some-ci-path";
        dockerVcsUrl = new URL("http://1.2.3.4:123");
        dockerCiUrl = new URL("http://5.6.7.8:123");
        ReflectionTestUtils.setField(jenkinsDockerUrlService, "dockerVcsUrl", Optional.empty());
        ReflectionTestUtils.setField(jenkinsDockerUrlService, "dockerCiUrl", Optional.empty());
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetVcsUrlOnDockerVcsUrlEmpty() {
        var newVcsUrl = jenkinsDockerUrlService.toDockerVcsUrl(vcsRepositoryUrl);
        assertThat(newVcsUrl).hasToString(vcsRepositoryUrl.toString());
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetVcsUrlOnDockerVcsUrl() {
        ReflectionTestUtils.setField(jenkinsDockerUrlService, "dockerVcsUrl", Optional.of(dockerVcsUrl));

        var newVcsUrl = jenkinsDockerUrlService.toDockerVcsUrl(vcsRepositoryUrl);
        assertThat(newVcsUrl).hasToString("http://1.2.3.4:123/some-repo.git");

        var vcsRepositoryUrl = mock(VcsRepositoryUrl.class);
        doReturn(null).when(vcsRepositoryUrl).getURL();
        assertThat(jenkinsDockerUrlService.toDockerVcsUrl(vcsRepositoryUrl)).isEqualTo(vcsRepositoryUrl);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetVcsUrlOnDockerVcsUrlMalformed() {
        ReflectionTestUtils.setField(jenkinsDockerUrlService, "dockerVcsUrl", Optional.of(dockerVcsUrl));
        doReturn("htt://invalid.com").when(jenkinsDockerUrlService).replaceUrl(eq(vcsRepositoryUrl.getURL().toString()), eq(dockerVcsUrl));
        var newVcsUrl = jenkinsDockerUrlService.toDockerVcsUrl(vcsRepositoryUrl);
        assertThat(newVcsUrl).hasToString(vcsRepositoryUrl.toString());
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetCiUrlOnDockerCiUrlEmpty() {
        var newCiUrl = jenkinsDockerUrlService.toDockerCiUrl(ciUrl);
        assertThat(newCiUrl).hasToString(ciUrl);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetCiUrlOnDockerCiUrl() {
        ReflectionTestUtils.setField(jenkinsDockerUrlService, "dockerCiUrl", Optional.of(dockerCiUrl));

        var newCiUrl = jenkinsDockerUrlService.toDockerCiUrl(ciUrl);
        assertThat(newCiUrl).hasToString("http://5.6.7.8:123/some-ci-path");

        assertThat(jenkinsDockerUrlService.toDockerCiUrl(null)).isNull();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetUrlOnDockerUrlWithoutPort() throws Exception {
        ReflectionTestUtils.setField(jenkinsDockerUrlService, "dockerCiUrl", Optional.of(new URL("http://www.host.name.com/")));
        ReflectionTestUtils.setField(jenkinsDockerUrlService, "dockerVcsUrl", Optional.of(new URL("http://www.hostname.com/")));

        var newVcsUrl = jenkinsDockerUrlService.toDockerVcsUrl(vcsRepositoryUrl);
        assertThat(newVcsUrl).hasToString("http://www.hostname.com/some-repo.git");

        var newCiUrl = jenkinsDockerUrlService.toDockerCiUrl(ciUrl);
        assertThat(newCiUrl).isEqualTo("http://www.host.name.com/some-ci-path");
    }
}
