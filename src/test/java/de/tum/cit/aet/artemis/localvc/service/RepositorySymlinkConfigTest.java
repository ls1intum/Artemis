package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for the handling of symbolic links in working copies, which Artemis does not create.
 */
class RepositorySymlinkConfigTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    private static final String DEFAULT_BRANCH = "main";

    @TempDir
    Path baseDir;

    private Path workingTree;

    @BeforeEach
    void setUp() throws Exception {
        workingTree = Files.createDirectories(baseDir.resolve("checkout"));
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch(DEFAULT_BRANCH).call().close();
        FileUtils.write(workingTree.resolve(".git").resolve("config").toFile(), "[core]\n\tsymlinks = true\n", StandardCharsets.UTF_8, true);
    }

    private Repository openedByArtemis() throws Exception {
        return AbstractGitService.linkRepositoryForExistingGit(workingTree, new LocalVCRepositoryUri(BASE_URI, "ABC", "abc-exercise"), DEFAULT_BRANCH, false, true);
    }

    @Test
    void symbolicLinksAreDisabledInTheConfiguration() throws Exception {
        try (Repository repository = openedByArtemis()) {
            assertThat(repository.getConfig().getBoolean(ConfigConstants.CONFIG_CORE_SECTION, ConfigConstants.CONFIG_KEY_SYMLINKS, true)).isFalse();
            // also after the configuration was changed in memory
            repository.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_SYMLINKS, true);
            assertThat(repository.getConfig().getBoolean(ConfigConstants.CONFIG_CORE_SECTION, ConfigConstants.CONFIG_KEY_SYMLINKS, true)).isFalse();
        }
    }

    @Test
    void theFileHoldsTheSettingAfterAnEarlierValueWasOverridden() throws Exception {
        // the first opening writes all settings, so that a later opening has nothing else to change
        openedByArtemis().close();
        Path configFile = workingTree.resolve(".git").resolve("config");
        FileUtils.write(configFile.toFile(), FileUtils.readFileToString(configFile.toFile(), StandardCharsets.UTF_8).replace("symlinks = false", "symlinks = true"),
                StandardCharsets.UTF_8);
        assertThat(configFile).content().contains("symlinks = true");

        openedByArtemis().close();

        assertThat(configFile).content().contains("symlinks = false").doesNotContain("symlinks = true");
    }
}
