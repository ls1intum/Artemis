package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for the repository configuration {@link AbstractGitService} applies whenever it opens a repository for
 * writing.
 * <p>
 * Every value it writes is a constant, so the second and every later call has nothing to write. That matters because
 * the repository store is on NFS, where saving the configuration and relinking HEAD are network round trips, and
 * preparing a large exam opens thousands of repositories. The configuration therefore has to be written exactly once,
 * and a value a previous version of Artemis - or a person - left malformed still has to be repaired rather than
 * failing the request that opened the repository.
 */
class AbstractGitServiceRepoConfigTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    private static final String DEFAULT_BRANCH = "main";

    private static final String PROJECT_KEY = "ABC";

    @TempDir
    Path baseDir;

    private Path createBareRepository(String repositorySlug) throws Exception {
        Path repositoryPath = baseDir.resolve(PROJECT_KEY).resolve(repositorySlug + ".git");
        Files.createDirectories(repositoryPath);
        Git.init().setDirectory(repositoryPath.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().close();
        return repositoryPath;
    }

    private Repository openForWriting(Path repositoryPath, String repositorySlug) throws Exception {
        return AbstractGitService.linkRepositoryForExistingGit(repositoryPath, new LocalVCRepositoryUri(BASE_URI, PROJECT_KEY, repositorySlug), DEFAULT_BRANCH, true, true);
    }

    /**
     * Reads a value back from the configuration on disk rather than from the instance that wrote it, so that a value
     * only held in memory cannot pass as persisted.
     *
     * @param repositoryPath the bare repository
     * @param section        the configuration section
     * @param key            the configuration key
     * @return the stored value, or {@link Integer#MIN_VALUE} if the key is absent
     */
    private static int storedInt(Path repositoryPath, String section, String key) throws IOException {
        try (org.eclipse.jgit.lib.Repository reopened = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build()) {
            return reopened.getConfig().getInt(section, null, key, Integer.MIN_VALUE);
        }
    }

    private static String storedString(Path repositoryPath, String section, String key) throws IOException {
        try (org.eclipse.jgit.lib.Repository reopened = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build()) {
            return reopened.getConfig().getString(section, null, key);
        }
    }

    @Test
    void setRepoConfig_onAFreshRepository_writesTheRequiredSettings() throws Exception {
        Path repositoryPath = createBareRepository("abc-fresh");

        openForWriting(repositoryPath, "abc-fresh").close();

        assertThat(storedInt(repositoryPath, ConfigConstants.CONFIG_GC_SECTION, ConfigConstants.CONFIG_KEY_AUTO)).as("automatic garbage collection is off").isZero();
        assertThat(storedString(repositoryPath, ConfigConstants.CONFIG_CORE_SECTION, ConfigConstants.CONFIG_KEY_SYMLINKS)).as("symlinks are disabled").isEqualTo("false");
    }

    @Test
    void setRepoConfig_calledAgain_doesNotWriteTheConfigurationOrRelinkHead() throws Exception {
        Path repositoryPath = createBareRepository("abc-twice");
        openForWriting(repositoryPath, "abc-twice").close();

        // Backdated rather than compared against the clock: a rewrite sets the modification time to now, whatever the
        // filesystem's timestamp granularity is.
        FileTime backdated = FileTime.from(Instant.now().minusSeconds(60));
        Path configFile = repositoryPath.resolve(Constants.CONFIG);
        Path headFile = repositoryPath.resolve(Constants.HEAD);
        Files.setLastModifiedTime(configFile, backdated);
        Files.setLastModifiedTime(headFile, backdated);

        openForWriting(repositoryPath, "abc-twice").close();

        assertThat(Files.getLastModifiedTime(configFile)).as("the configuration is not written again once it holds the required values").isEqualTo(backdated);
        assertThat(Files.getLastModifiedTime(headFile)).as("HEAD is not relinked once it points at the default branch").isEqualTo(backdated);
    }

    @Test
    void setRepoConfig_withAMalformedNumber_repairsItInsteadOfFailing() throws Exception {
        Path repositoryPath = createBareRepository("abc-malformed-number");
        try (org.eclipse.jgit.lib.Repository repository = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build()) {
            repository.getConfig().setString(ConfigConstants.CONFIG_GC_SECTION, null, ConfigConstants.CONFIG_KEY_AUTO, "invalid");
            repository.getConfig().save();
        }

        // JGit throws while parsing such a value instead of falling back to a default, so reading it before writing it
        // has to tolerate that, or opening the repository fails for as long as the value stays on disk.
        assertThatCode(() -> openForWriting(repositoryPath, "abc-malformed-number").close()).as("a malformed number does not fail the request").doesNotThrowAnyException();

        assertThat(storedInt(repositoryPath, ConfigConstants.CONFIG_GC_SECTION, ConfigConstants.CONFIG_KEY_AUTO)).as("the malformed number is overwritten").isZero();
    }

    @Test
    void setRepoConfig_withAMalformedBoolean_repairsItInsteadOfFailing() throws Exception {
        Path repositoryPath = createBareRepository("abc-malformed-boolean");
        try (org.eclipse.jgit.lib.Repository repository = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build()) {
            repository.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_SYMLINKS, "invalid");
            repository.getConfig().save();
        }

        assertThatCode(() -> openForWriting(repositoryPath, "abc-malformed-boolean").close()).as("a malformed boolean does not fail the request").doesNotThrowAnyException();

        // Symlinks stay disabled deliberately: a repository that keeps a value Artemis cannot read is a repository
        // where the setting that exists to prevent remote code execution is not in force.
        assertThat(storedString(repositoryPath, ConfigConstants.CONFIG_CORE_SECTION, ConfigConstants.CONFIG_KEY_SYMLINKS)).as("the malformed boolean is overwritten")
                .isEqualTo("false");
    }

    @Test
    void setRepoConfig_withHeadPointingElsewhere_relinksItToTheDefaultBranch() throws Exception {
        Path repositoryPath = createBareRepository("abc-wrong-head");
        FileUtils.write(repositoryPath.resolve(Constants.HEAD).toFile(), "ref: refs/heads/some-other-branch\n", StandardCharsets.UTF_8);

        openForWriting(repositoryPath, "abc-wrong-head").close();

        assertThat(Files.readString(repositoryPath.resolve(Constants.HEAD))).as("HEAD is relinked to the default branch").contains("refs/heads/" + DEFAULT_BRANCH);
    }
}
