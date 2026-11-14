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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for filter drivers, which are not run in checked out repositories as Artemis does not use them.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the filter driver is a shell command")
class RepositoryFilterDriverTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    private static final String DEFAULT_BRANCH = "main";

    private static final String DRIVER = "custom";

    @TempDir
    Path baseDir;

    private Path workingTree;

    private Path marker;

    @BeforeEach
    void setUp() throws Exception {
        workingTree = Files.createDirectories(baseDir.resolve("checkout"));
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch(DEFAULT_BRANCH).call().close();
        marker = baseDir.resolve("filter-ran");
        FileUtils.write(workingTree.resolve(".git").resolve("config").toFile(),
                "[filter \"" + DRIVER + "\"]\n\tclean = touch '" + marker + "'; cat\n\tsmudge = touch '" + marker + "'; cat\n", StandardCharsets.UTF_8, true);
        FileUtils.write(workingTree.resolve(".gitattributes").toFile(), "*.txt filter=" + DRIVER + "\n", StandardCharsets.UTF_8);
    }

    private Repository openedByArtemis() throws Exception {
        return AbstractGitService.linkRepositoryForExistingGit(workingTree, new LocalVCRepositoryUri(BASE_URI, "ABC", "abc-exercise"), DEFAULT_BRANCH, false, true);
    }

    @Test
    void filterDriversOfTheRepositoryConfigurationAreRemoved() throws Exception {
        try (Repository repository = openedByArtemis()) {
            assertThat(repository.getConfig().getString(ConfigConstants.CONFIG_FILTER_SECTION, DRIVER, "clean")).isNull();
            assertThat(repository.getConfig().getString(ConfigConstants.CONFIG_FILTER_SECTION, DRIVER, "smudge")).isNull();
        }
    }

    @Test
    void addingAFileDoesNotRunTheFilterDriver() throws Exception {
        try (Repository repository = openedByArtemis(); Git git = new Git(repository)) {
            FileUtils.write(workingTree.resolve("file.txt").toFile(), "content", StandardCharsets.UTF_8);
            git.add().addFilepattern("file.txt").call();
            GitService.commit(git).setMessage("Add file").setCommitter("Artemis", "artemis@example.com").call();
        }
        assertThat(marker).doesNotExist();
    }
}
