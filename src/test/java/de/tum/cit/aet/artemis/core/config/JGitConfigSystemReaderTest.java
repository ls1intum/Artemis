package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The three configuration files above a repository must not be read, and must not be looked for.
 * <p>
 * Looking for them is the expensive part: they do not exist on a server, so every check is a failed stat that becomes
 * a {@code NoSuchFileException} with a captured stack trace, tens of thousands of times per benchmark run.
 */
class JGitConfigSystemReaderTest {

    private SystemReader original;

    @BeforeEach
    void rememberReader() {
        original = SystemReader.getInstance();
    }

    @AfterEach
    void restoreReader() {
        SystemReader.setInstance(original);
    }

    @Test
    @DisplayName("The user, system and jgit configs are empty and never stat their file")
    void configsAreEmptyAndNeverStatted() {
        new JGitConfig().useRepositoryGitConfigurationOnly();
        SystemReader reader = SystemReader.getInstance();

        for (FileBasedConfig config : new FileBasedConfig[] { reader.openUserConfig(null, FS.DETECTED), reader.openSystemConfig(null, FS.DETECTED),
                reader.openJGitConfig(null, FS.DETECTED) }) {
            assertThat(config.getFile()).as("backed by no file, so there is nothing to stat").isNull();
            assertThat(config.isOutdated()).as("a file that does not exist cannot go out of date").isFalse();
            assertThat(config.getSections()).as("holds no settings from the machine").isEmpty();
        }
    }

    @Test
    @DisplayName("A repository's own configuration is still read")
    void repositoryConfigurationStillApplies(@TempDir Path directory) throws Exception {
        new JGitConfig().useRepositoryGitConfigurationOnly();

        try (Git git = Git.init().setDirectory(directory.toFile()).call(); Repository repository = git.getRepository()) {
            repository.getConfig().setString("user", null, "name", "Artemis");
            repository.getConfig().save();

            // Re-open, so the value has to come back off disk rather than out of the instance just written to.
            try (Repository reopened = Git.open(directory.toFile()).getRepository()) {
                assertThat(reopened.getConfig().getString("user", null, "name")).isEqualTo("Artemis");
            }
        }
    }

    @Test
    @DisplayName("A user config that does exist is still ignored, so nodes cannot drift apart")
    void aPresentUserConfigIsIgnored(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve(".gitconfig"), "[core]\n\tautocrlf = true\n");
        new JGitConfig().useRepositoryGitConfigurationOnly();

        FS fs = FS.DETECTED.newInstance();
        fs.setUserHome(home.toFile());
        FileBasedConfig userConfig = SystemReader.getInstance().openUserConfig(null, fs);
        userConfig.load();

        assertThat(userConfig.getString("core", null, "autocrlf")).as("the machine's git configuration must not reach Artemis").isNull();
    }

}
