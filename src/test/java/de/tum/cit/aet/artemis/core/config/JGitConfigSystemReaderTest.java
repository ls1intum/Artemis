package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The three configuration files above a repository must not be read, and must not be looked for.
 * <p>
 * Looking for them is the expensive part: they do not exist on a server, so every check is a failed stat that becomes a
 * {@code NoSuchFileException} with a captured stack trace, tens of thousands of times per benchmark run.
 * <p>
 * The reader is exercised directly rather than through {@link SystemReader#setInstance}. Installing it would reset
 * JGit's static platform detection caches while other test classes are running git operations in parallel, which is the
 * race {@code JGitSystemReaderInitializer} exists to prevent and {@code ArchitectureTest} forbids.
 */
class JGitConfigSystemReaderTest {

    private SystemReader repositoryOnlyReader() {
        return new JGitConfig.RepositoryOnlyConfigReader(SystemReader.getInstance());
    }

    @Test
    @DisplayName("The user, system and jgit configs are empty and never stat their file")
    void configsAreEmptyAndNeverStatted() {
        SystemReader reader = repositoryOnlyReader();

        for (FileBasedConfig config : new FileBasedConfig[] { reader.openUserConfig(null, FS.DETECTED), reader.openSystemConfig(null, FS.DETECTED),
                reader.openJGitConfig(null, FS.DETECTED) }) {
            assertThat(config.getFile()).as("backed by no file, so there is nothing to stat").isNull();
            assertThat(config.isOutdated()).as("a file that does not exist cannot go out of date").isFalse();
            assertThat(config.getSections()).as("holds no settings from the machine").isEmpty();
        }
    }

    @Test
    @DisplayName("A user config that does exist is ignored, so nodes cannot drift apart")
    void aPresentUserConfigIsIgnored(@TempDir Path home) throws Exception {
        FileUtils.writeStringToFile(home.resolve(".gitconfig").toFile(), "[core]\n\tautocrlf = true\n", StandardCharsets.UTF_8);
        FS fs = FS.DETECTED.newInstance();
        fs.setUserHome(home.toFile());

        FileBasedConfig userConfig = repositoryOnlyReader().openUserConfig(null, fs);
        userConfig.load();

        assertThat(userConfig.getString("core", null, "autocrlf")).as("the machine's git configuration must not reach Artemis").isNull();
    }

    @Test
    @DisplayName("A repository's own configuration is still read")
    void repositoryConfigurationStillApplies(@TempDir Path directory) throws Exception {
        // The reader only replaces the three configs above a repository, so the repository's own must be unaffected.
        // This runs against whatever reader the test plan installed, which is the same one under test.
        try (Git git = Git.init().setDirectory(directory.toFile()).call(); Repository repository = git.getRepository()) {
            repository.getConfig().setString("user", null, "name", "Artemis");
            repository.getConfig().save();
        }

        File repositoryDirectory = directory.toFile();
        try (Git reopened = Git.open(repositoryDirectory); Repository repository = reopened.getRepository()) {
            assertThat(repository.getConfig().getString("user", null, "name")).isEqualTo("Artemis");
        }
    }

    @Test
    @DisplayName("Installing the reader is idempotent, so it cannot reset JGit's platform caches twice")
    void installationIsIdempotent() {
        // GlobalCleanupListener already installed it before the test plan started.
        assertThat(JGitConfig.isSystemReaderConfigured()).isTrue();
        assertThat(JGitConfig.configureSystemReaderOnce()).as("a second call must not install anything").isFalse();
    }
}
