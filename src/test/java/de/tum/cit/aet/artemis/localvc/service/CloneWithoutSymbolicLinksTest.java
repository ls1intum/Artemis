package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test that the first checkout of a clone does not create symbolic links, and that a clone is otherwise checked out as usual.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the test creates a symbolic link")
class CloneWithoutSymbolicLinksTest {

    @TempDir
    Path baseDir;

    @Test
    void aSymbolicLinkOfTheRemoteIsCheckedOutAsAPlainFile() throws Exception {
        Path source = Files.createDirectories(baseDir.resolve("source"));
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch("main").call()) {
            FileUtils.write(source.resolve("Main.java").toFile(), "class Main {}", StandardCharsets.UTF_8);
            Files.createSymbolicLink(source.resolve("link"), Path.of("Main.java"));
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("content").setAuthor("a", "a@example.com").setCommitter("a", "a@example.com").setSign(false).call();
        }
        Path target = baseDir.resolve("target");

        AbstractGitService.cloneWithoutSymbolicLinks(Git.cloneRepository().setURI(source.toUri().toString()).setDirectory(target.toFile()).setBranch("main"));

        assertThat(Files.isSymbolicLink(target.resolve("link"))).as("the link is not created").isFalse();
        assertThat(Files.isRegularFile(target.resolve("link"))).isTrue();
        assertThat(target.resolve("Main.java")).hasContent("class Main {}");
        try (Git git = Git.open(target.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo("main");
            assertThat(git.getRepository().resolve(Constants.HEAD)).isNotNull();
        }
    }

    @Test
    void anEmptyRemoteIsCloned() throws Exception {
        Path source = baseDir.resolve("empty-source");
        Git.init().setDirectory(source.toFile()).setInitialBranch("main").call().close();
        Path target = baseDir.resolve("empty-target");

        AbstractGitService.cloneWithoutSymbolicLinks(Git.cloneRepository().setURI(source.toUri().toString()).setDirectory(target.toFile()));

        assertThat(target.resolve(".git")).isDirectory();
    }
}
