package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.localvc.service.GitService;

class DeimosSnapshotReaderTest {

    private final DeimosSnapshotReader deimosSnapshotReader = new DeimosSnapshotReader();

    @TempDir
    private Path workingTree;

    @Test
    void readKeepsScriptsAndTextAndSkipsOnlyFilesWhoseExtensionAndContentAreBinary() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            FileUtils.writeStringToFile(workingTree.resolve("Main.java").toFile(), "class Main {}\n", StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(workingTree.resolve("build.sh").toFile(), "#!/bin/sh\necho build\n", StandardCharsets.UTF_8);
            // A shell executes a script despite a NUL byte, so a script is never classified away by its content.
            FileUtils.writeByteArrayToFile(workingTree.resolve("RUN.BAT").toFile(), "@echo off\r\nrem \0\r\necho run\r\n".getBytes(StandardCharsets.UTF_8));
            // Without a binary extension a file stays text even with a NUL byte, so such a byte cannot hide it either.
            FileUtils.writeByteArrayToFile(workingTree.resolve("gradlew").toFile(), "#!/bin/sh\n# \0\necho wrapper\n".getBytes(StandardCharsets.UTF_8));
            FileUtils.writeByteArrayToFile(workingTree.resolve("logo.png").toFile(), new byte[] { (byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 13 });
            String commitHash = commitAll(git);

            DeimosSnapshotReader.TextSnapshot snapshot = deimosSnapshotReader.read(git.getRepository(), commitHash);

            assertThat(snapshot.textFiles()).containsOnlyKeys("Main.java", "build.sh", "RUN.BAT", "gradlew").containsEntry("build.sh", "#!/bin/sh\necho build\n");
            assertThat(snapshot.otherPaths()).containsExactly("logo.png");
        }
    }

    @Test
    void readReturnsAnEmptySnapshotForACommitThatCannotBeResolved() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            DeimosSnapshotReader.TextSnapshot snapshot = deimosSnapshotReader.read(git.getRepository(), "no-such-commit");

            assertThat(snapshot.textFiles()).isEmpty();
            assertThat(snapshot.otherPaths()).isEmpty();
        }
    }

    private static String commitAll(Git git) throws Exception {
        git.add().addFilepattern(".").call();
        var author = new PersonIdent("Artemis", "artemis@example.com");
        return GitService.commit(git).setMessage("Files").setAuthor(author).setCommitter(author).call().getName();
    }
}
