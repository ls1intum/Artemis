package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

class RepositoryUriTest {

    @Test
    void testValidUriWithGit() {
        String uriString = "https://artemis.tum.de/git/key/key-repositoryslug.git";

        assertThatCode(() -> {
            LocalVCRepositoryUri uri = new LocalVCRepositoryUri(uriString);

            // Assuming getters are available
            assertThat(uri.getURI()).isEqualTo(new URI(uriString)); // Checks if the URI is set correctly
            assertThat(uri.getProjectKey()).isEqualTo("key"); // Checks if the project key is extracted correctly
            assertThat(uri.getLocalRepositoryPath("test").toString()).isEqualTo("test/key/key-repositoryslug.git"); // Checks if the repository slug is extracted correctly
            assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("repositoryslug"); // Checks if the repository type or username is parsed correctly
            assertThat(uri.isPracticeRepository()).isFalse(); // Checks the practice repository flag

        }).doesNotThrowAnyException();
    }

    @Test
    void testUriWithoutGit() {
        String uriString = "https://artemis.tum.de/key/key-repositoryslug.git";
        assertThatThrownBy(() -> new LocalVCRepositoryUri(uriString)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("Invalid Local VC Repository URI: 'git' directory not found in the URI");
    }

    @Test
    void testUriWithInsufficientSegments() {
        String uriString = "https://artemis.tum.de/git/key";
        assertThatThrownBy(() -> new LocalVCRepositoryUri(uriString)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("URI does not contain enough segments after 'git'");
    }

    @Test
    void testUriRepositorySlugWithoutGitSuffix() {
        String uriString = "https://artemis.tum.de/git/key/key-repositoryslug";
        assertThatThrownBy(() -> new LocalVCRepositoryUri(uriString)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("Repository slug segment 'key-repositoryslug' does not end with '.git'");
    }

    @Test
    void testLocalRepositoryPath() throws Exception {
        Path repositoryPath = Path.of("/local/path/projectX/projectX-repo/.git");
        URI localVCServerUri = new URI("https://artemis.tum.de");
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(repositoryPath, localVCServerUri);

        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.tum.de/git/projectX/projectX-repo.git");
        assertThat(uri.getProjectKey()).isEqualTo("projectX");
        assertThat(uri.repositorySlug()).isEqualTo("projectX-repo");
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("repo");
        assertThat(uri.isPracticeRepository()).isFalse();
    }

    @Test
    void testLocalRepositoryPath_testExamAttempt() throws Exception {
        String projectKey = "projectX23";
        String repositorySlug = "projectX23-my-repo";
        URI localVCBaseUri = new URI("https://artemis.cit.tum.de");

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(projectKey, repositorySlug, localVCBaseUri);

        assertThat(uri.getProjectKey()).isEqualTo(projectKey);
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("my-repo");
        assertThat(uri.isPracticeRepository()).isFalse();
        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.cit.tum.de/git/projectX23/projectX23-my-repo.git");
    }

    @Test
    void testRemoteRepositoryPath() throws Exception {
        Path repositoryPath = Path.of("/remote/path/projectY/projectY-repo");
        URI localVCServerUri = new URI("https://artemis.tum.de");
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(repositoryPath, localVCServerUri);

        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.tum.de/git/projectY/projectY-repo.git");
        assertThat(uri.getProjectKey()).isEqualTo("projectY");
        assertThat(uri.repositorySlug()).isEqualTo("projectY-repo");
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("repo");
        assertThat(uri.isPracticeRepository()).isFalse();
    }

    @Test
    void testInvalidRepositoryPath() {
        Path repositoryPath = Path.of("/invalid/path");
        URI localVCServerUri;
        try {
            localVCServerUri = new URI("https://artemis.tum.de");
            assertThatThrownBy(() -> new LocalVCRepositoryUri(repositoryPath, localVCServerUri)).isInstanceOf(LocalVCInternalException.class)
                    .hasMessageContaining("Invalid project key and repository slug: invalid, path");
        }
        catch (Exception e) {
            fail("Setup of URI failed", e);
        }
    }

    @Test
    void testConstructorWithValidData() throws Exception {
        String projectKey = "projectX";
        String repositorySlug = "my-repo";
        URI localVCBaseUri = new URI("https://artemis.tum.de");

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(projectKey, repositorySlug, localVCBaseUri);

        assertThat(uri.getProjectKey()).isEqualTo(projectKey);
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo(repositorySlug);
        assertThat(uri.isPracticeRepository()).isFalse();
        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.tum.de/git/projectX/my-repo.git");
    }

    @Test
    void testConstructorWithPracticeRepository() throws Exception {
        String projectKey = "projectX";
        String repositorySlug = "projectX-practice-my-repo";
        URI localVCBaseUri = new URI("https://artemis.tum.de");

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(projectKey, repositorySlug, localVCBaseUri);

        assertThat(uri.getProjectKey()).isEqualTo(projectKey);
        assertThat(uri.getRepositoryTypeOrUserName()).isEqualTo("my-repo");
        assertThat(uri.isPracticeRepository()).isTrue();
        assertThat(uri.getURI().toString()).isEqualTo("https://artemis.tum.de/git/projectX/projectX-practice-my-repo.git");
    }

    @Test
    void testConstructorWithMalformedURI() {
        String malformedBaseUri = "htp://invalid uri";

        assertThatThrownBy(() -> {
            new URI(malformedBaseUri);  // This check is to simulate the situation where URI is incorrect.
        }).isInstanceOf(URISyntaxException.class);
        assertThatThrownBy(() -> {
            new LocalVCRepositoryUri(malformedBaseUri);  // This check is to simulate the situation where URI is incorrect.
        }).isInstanceOf(LocalVCInternalException.class);
    }

    @Test
    void testConstructorWithInvalidURI() {
        String projectKey = "projectX";
        String repositorySlug = "my-repo";
        String malformedBaseUri = "https://invalid-uri.de/h?s=^112";

        assertThatThrownBy(() -> {
            new URI(malformedBaseUri);  // This check is to simulate the situation where URI is incorrect.
            new LocalVCRepositoryUri(projectKey, repositorySlug, new URI(malformedBaseUri));
        }).isInstanceOf(URISyntaxException.class);
    }

    @Test
    void testConstructorWithValidUriString() {
        String uriSpecString = "https://artemistest2.aet.cit.tum.de/git/FTCSCAGRADING1/ftcscagrading1-username.git";
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(uriSpecString);
        assertThat(uri.getURI().toString()).isEqualTo(uriSpecString);
    }

    @Test
    void testConstructorWithInvalidUriString() {
        String invalidUriSpecString = "https://malformed-uri.de/h?s=^123";
        assertThatThrownBy(() -> new LocalVCRepositoryUri(invalidUriSpecString)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("Could not create local VC Repository URI");
    }

    @Test
    void testConstructorWithFile() {
        File file = Path.of("/path/to/repo/git/name/name-slug.git").toFile();
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(file.getPath());
        assertThat(uri.getURI().toString()).isEqualTo(file.getPath());
    }

    @Test
    void testFolderNameForRepositoryUriWithFileUri() throws URISyntaxException {
        URI fileUri = new URI("file:///path/to/repo/git/projectName/projectName-slug.git");
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(fileUri.toString());
        assertThat(uri.folderNameForRepositoryUri()).isEqualTo("projectName-slug.git");
    }

    @Test
    void testFolderNameForRepositoryUriWithHttpUri() throws URISyntaxException {
        URI httpUri = new URI("https://example.com/git/projectName/projectName-slug.git");
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(httpUri.toString());
        assertThat(uri.folderNameForRepositoryUri()).isEqualTo("projectName/projectName-slug");
    }

    @Test
    void testEqualsAndHashCodeAndToString() throws URISyntaxException {
        URI uri1 = new URI("https://example.com/git/projectName/projectName-slug.git");
        URI uri2 = new URI("https://example.com/git/projectName/projectName-slug.git");
        LocalVCRepositoryUri vcsUri1 = new LocalVCRepositoryUri(uri1.toString());
        LocalVCRepositoryUri vcsUri2 = new LocalVCRepositoryUri(uri2.toString());

        assertThat(vcsUri1).isEqualTo(vcsUri2);
        assertThat(vcsUri1.hashCode()).isEqualTo(vcsUri2.hashCode());
        assertThat(vcsUri1.toString()).isEqualTo("https://example.com/git/projectName/projectName-slug.git");
    }

    @Test
    void testRepositoryNameWithoutProjectKey() throws URISyntaxException {
        URI uri = new URI("https://example.com/git/GREAT/great-artemis_admin.git");
        LocalVCRepositoryUri vcsUri = new LocalVCRepositoryUri(uri.toString());
        assertThat(vcsUri.repositoryNameWithoutProjectKey()).isEqualTo("artemis_admin");
    }

    @Test
    void testRepositorySlug() throws URISyntaxException {
        URI uri = new URI("https://example.com/git/projectName/projectName-projectslug.git");
        LocalVCRepositoryUri vcsUri = new LocalVCRepositoryUri(uri.toString());
        assertThat(vcsUri.repositorySlug()).isEqualTo("projectName-projectslug");
    }

    @Test
    void testRepositoryNameAndProjectKey() throws URISyntaxException {
        URI uri = new URI("https://example.com/git/projectKey/projectKey-repositorySlug.git");
        LocalVCRepositoryUri vcsUri = new LocalVCRepositoryUri(uri.toString());
        assertThat(vcsUri.repositoryNameWithoutProjectKey()).isEqualTo("repositoryslug");  // The result is in lowercase
    }
}
