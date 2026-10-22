package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for where {@link GitService#getLocalPathOfRepo} places a checked out repository: strictly below the target path.
 */
class GitServiceLocalPathTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    @TempDir
    Path repoClonePath;

    private GitService gitService;

    @BeforeEach
    void setUp() {
        gitService = new GitService();
    }

    /**
     * A repository URI whose folder name is the given one, to test the check on the resolved path independently of how the URI derives it.
     */
    private static LocalVCRepositoryUri repositoryUriWithFolderName(String folderName) {
        return new LocalVCRepositoryUri(BASE_URI, "ABC", "abc-exercise") {

            @Override
            public String folderNameForRepositoryUri() {
                return folderName;
            }
        };
    }

    @Test
    void getLocalPathOfRepo_resolvesBelowTheTargetPath() {
        Path localPath = gitService.getLocalPathOfRepo(repoClonePath, new LocalVCRepositoryUri(BASE_URI, "ABC", "abc-exercise"));

        assertThat(localPath).isEqualTo(repoClonePath.resolve("ABC").resolve("abc-exercise"));
    }

    @Test
    void getLocalPathOfRepo_forARepositoryWithAnotherName_resolvesBelowTheTargetPath() {
        Path localPath = gitService.getLocalPathOfRepo(repoClonePath, new LocalVCRepositoryUri(BASE_URI, "ABC", "abc-scm"));

        assertThat(localPath).isEqualTo(repoClonePath.resolve("ABC").resolve("abc-scm"));
    }

    @Test
    void getLocalPathOfRepo_forAUriWithFurtherPathSegments_resolvesToTheFolderOfTheRepository() {
        Path localPath = gitService.getLocalPathOfRepo(repoClonePath, new LocalVCRepositoryUri("https://artemis.example.com/git/ABC/abc-exercise.git/.."));

        assertThat(localPath).isEqualTo(repoClonePath.resolve("ABC").resolve("abc-exercise"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://artemis.example.com/git/../..x.git", "https://artemis.example.com/git/./..git" })
    void getLocalPathOfRepo_forAProjectKeyThatIsNotAFolderName_isRejected(String uriString) {
        var repositoryUri = new LocalVCRepositoryUri(uriString);

        assertThatExceptionOfType(IllegalArgumentException.class).as("the URI '%s' must be rejected", uriString)
                .isThrownBy(() -> gitService.getLocalPathOfRepo(repoClonePath, repositoryUri));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", ".", "ABC/..", "ABC/../..", "../outside", "ABC/../../outside" })
    void getLocalPathOfRepo_forAFolderNameThatIsNotBelowTheTargetPath_isRejected(String folderName) {
        assertThatExceptionOfType(IllegalArgumentException.class).as("the folder name '%s' must be rejected", folderName)
                .isThrownBy(() -> gitService.getLocalPathOfRepo(repoClonePath, repositoryUriWithFolderName(folderName)));
    }
}
