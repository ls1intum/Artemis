package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

class RepositoryUriTest {

    @Test
    void testValidUrlWithGit() {
        String urlString = "https://artemis.cit.tum.de/git/key/key-repositoryslug.git";

        assertThatCode(() -> {
            LocalVCRepositoryUri uri = new LocalVCRepositoryUri(urlString);

            // Assuming getters are available
            assertThat(uri.getURI()).isEqualTo(new URI(urlString)); // Checks if the URI is set correctly
            assertThat(uri.getProjectKey()).isEqualTo("key"); // Checks if the project key is extracted correctly
            assertThat(uri.getLocalRepositoryPath("test").toString()).isEqualTo("test/key/key-repositoryslug.git"); // Checks if the repository slug is extracted correctly
            assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("repositoryslug"); // Checks if the repository type or username is parsed correctly
            assertThat(uri.isPracticeRepository()).isFalse(); // Checks the practice repository flag

        }).doesNotThrowAnyException();
    }

    @Test
    void testUrlWithoutGit() {
        String urlString = "https://artemis.cit.tum.de/key/key-repositoryslug.git";
        assertThatThrownBy(() -> new LocalVCRepositoryUri(urlString)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("Invalid local VC Repository URI: 'git' directory not found in the URL");
    }

    @Test
    void testUrlWithInsufficientSegments() {
        String urlString = "https://artemis.cit.tum.de/git/key";
        assertThatThrownBy(() -> new LocalVCRepositoryUri(urlString)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("URL does not contain enough segments after 'git'");
    }

    @Test
    void testUrlRepositorySlugWithoutGitSuffix() {
        String urlString = "https://artemis.cit.tum.de/git/key/key-repositoryslug";
        assertThatThrownBy(() -> new LocalVCRepositoryUri(urlString)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("Repository slug segment 'key-repositoryslug' does not end with '.git'");
    }

    @Test
    void testLocalRepositoryPath() throws Exception {
        Path repositoryPath = Paths.get("/local/path/projectX/projectX-repo/.git");
        URL localVCServerUrl = new URI("https://artemis.cit.tum.de").toURL();
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(repositoryPath, localVCServerUrl);

        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.cit.tum.de/git/projectX/projectX-repo.git");
        assertThat(uri.getProjectKey()).isEqualTo("projectX");
        assertThat(uri.repositorySlug()).isEqualTo("projectX-repo");
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("repo");
        assertThat(uri.isPracticeRepository()).isFalse();
    }

    @Test
    void testRemoteRepositoryPath() throws Exception {
        Path repositoryPath = Paths.get("/remote/path/projectY/projectY-repo");
        URL localVCServerUrl = new URI("https://artemis.cit.tum.de").toURL();
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(repositoryPath, localVCServerUrl);

        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.cit.tum.de/git/projectY/projectY-repo.git");
        assertThat(uri.getProjectKey()).isEqualTo("projectY");
        assertThat(uri.repositorySlug()).isEqualTo("projectY-repo");
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("repo");
        assertThat(uri.isPracticeRepository()).isFalse();
    }

    @Test
    void testInvalidRepositoryPath() {
        Path repositoryPath = Paths.get("/invalid/path");
        URL localVCServerUrl;
        try {
            localVCServerUrl = new URI("https://artemis.cit.tum.de").toURL();
            assertThatThrownBy(() -> new LocalVCRepositoryUri(repositoryPath, localVCServerUrl)).isInstanceOf(LocalVCInternalException.class)
                    .hasMessageContaining("Invalid project key and repository slug: invalid, path");
        }
        catch (Exception e) {
            fail("Setup of URL failed", e);
        }
    }

    @Test
    void testConstructorWithValidData() throws Exception {
        String projectKey = "projectX";
        String repositorySlug = "my-repo";
        URL localVCBaseUrl = new URI("https://artemis.cit.tum.de").toURL();

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(projectKey, repositorySlug, localVCBaseUrl);

        assertThat(uri.getProjectKey()).isEqualTo(projectKey);
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo(repositorySlug);
        assertThat(uri.isPracticeRepository()).isFalse();
        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.cit.tum.de/git/projectX/my-repo.git");
    }

    @Test
    void testConstructorWithPracticeRepository() throws Exception {
        String projectKey = "projectX";
        String repositorySlug = "projectX-practice-my-repo";
        URL localVCBaseUrl = new URI("https://artemis.cit.tum.de").toURL();

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(projectKey, repositorySlug, localVCBaseUrl);

        assertThat(uri.getProjectKey()).isEqualTo(projectKey);
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("my-repo");
        assertThat(uri.isPracticeRepository()).isTrue();
        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.cit.tum.de/git/projectX/projectX-practice-my-repo.git");
    }

    @Test
    void testConstructorWithMalformedURI() {
        String projectKey = "projectX";
        String repositorySlug = "my-repo";
        String malformedBaseUrl = "htp://invalid-url";

        assertThatThrownBy(() -> {
            new URI(malformedBaseUrl).toURL();  // This check is to simulate the situation where URL is incorrect.
            new LocalVCRepositoryUri(projectKey, repositorySlug, new URI(malformedBaseUrl).toURL());
        }).isInstanceOf(MalformedURLException.class);
    }

    @Test
    void testConstructorWithInvalidURI() {
        String projectKey = "projectX";
        String repositorySlug = "my-repo";
        String malformedBaseUrl = "https://invalid-url.de/h?s=^112";

        assertThatThrownBy(() -> {
            new URI(malformedBaseUrl).toURL();  // This check is to simulate the situation where URL is incorrect.
            new LocalVCRepositoryUri(projectKey, repositorySlug, new URI(malformedBaseUrl).toURL());
        }).isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testConstructorWithValidUriString() throws URISyntaxException {
        String uriSpecString = "https://artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username";
        VcsRepositoryUri uri = new VcsRepositoryUri(uriSpecString);
        assertThat(uri.getURI().toString()).isEqualTo(uriSpecString);
    }

    @Test
    void testConstructorWithInvalidUriString() {
        String invalidUriSpecString = "https://malformed-uri.de/h?s=^123";
        assertThatThrownBy(() -> new VcsRepositoryUri(invalidUriSpecString)).isInstanceOf(URISyntaxException.class).hasMessageContaining("Illegal character");
    }

    @Test
    void testConstructorWithFile() {
        File file = new File("/path/to/repo");
        VcsRepositoryUri uri = new VcsRepositoryUri(file);
        assertThat(uri.getURI().toString()).isEqualTo(file.toURI().toString());
    }

    @Test
    void testFolderNameForRepositoryUriWithFileUri() throws URISyntaxException {
        URI fileUri = new URI("file:///path/to/repo/projectName");
        VcsRepositoryUri uri = new VcsRepositoryUri(fileUri.toString());
        assertThat(uri.folderNameForRepositoryUri()).isEqualTo("projectName");
    }

    @Test
    void testFolderNameForRepositoryUriWithHttpUri() throws URISyntaxException {
        URI httpUri = new URI("https://example.com/git/projectName.git");
        VcsRepositoryUri uri = new VcsRepositoryUri(httpUri.toString());
        assertThat(uri.folderNameForRepositoryUri()).isEqualTo("/projectName");
    }

    @Test
    void testEqualsAndHashCodeAndToString() throws URISyntaxException {
        URI uri1 = new URI("https://example.com/git/projectName.git");
        URI uri2 = new URI("https://example.com/git/projectName.git");
        VcsRepositoryUri vcsUri1 = new VcsRepositoryUri(uri1.toString());
        VcsRepositoryUri vcsUri2 = new VcsRepositoryUri(uri2.toString());

        assertThat(vcsUri1).isEqualTo(vcsUri2);
        assertThat(vcsUri1.hashCode()).isEqualTo(vcsUri2.hashCode());
        assertThat(vcsUri1.toString()).isEqualTo("https://example.com/git/projectName.git");
    }

    @Test
    void testRepositoryNameWithoutProjectKey() throws URISyntaxException {
        URI uri = new URI("https://example.com/git/GREAT/great-artemis_admin.git");
        VcsRepositoryUri vcsUri = new VcsRepositoryUri(uri.toString());
        assertThat(vcsUri.repositoryNameWithoutProjectKey()).isEqualTo("artemis_admin");
    }

    @Test
    void testRepositorySlug() throws URISyntaxException {
        URI uri = new URI("https://example.com/git/projectName/project-slug.git");
        VcsRepositoryUri vcsUri = new VcsRepositoryUri(uri.toString());
        assertThat(vcsUri.repositorySlug()).isEqualTo("project-slug");
    }

    @Test
    void testRepositoryNameAndProjectKey() throws URISyntaxException {
        URI uri = new URI("https://example.com/git/projectKey/repositorySlug.git");
        VcsRepositoryUri vcsUri = new VcsRepositoryUri(uri.toString());
        assertThat(vcsUri.repositoryNameWithoutProjectKey()).isEqualTo("repositoryslug");  // The result is in lowercase
    }
}
