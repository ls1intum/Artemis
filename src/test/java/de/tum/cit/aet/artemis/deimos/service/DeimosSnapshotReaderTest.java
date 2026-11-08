package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
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
            // A binary extension alone does not hide a file: its content has to be binary as well.
            FileUtils.writeStringToFile(workingTree.resolve("notes.bin").toFile(), "plain text\n", StandardCharsets.UTF_8);
            byte[] logo = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 13 };
            FileUtils.writeByteArrayToFile(workingTree.resolve("logo.png").toFile(), logo);
            String commitHash = commitAll(git);

            DeimosSnapshotReader.TextSnapshot snapshot = deimosSnapshotReader.read(git.getRepository(), commitHash);

            assertThat(snapshot.textFiles()).containsOnlyKeys("Main.java", "build.sh", "RUN.BAT", "gradlew", "notes.bin").containsEntry("build.sh", "#!/bin/sh\necho build\n");
            assertThat(snapshot.otherFiles()).containsExactly(entry("logo.png", blobId(logo)));
        }
    }

    @Test
    void readReportsSymbolicLinksAndSubmodulesWithTheObjectTheyPointTo() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            FileUtils.writeStringToFile(workingTree.resolve("Main.java").toFile(), "class Main {}\n", StandardCharsets.UTF_8);
            Files.createSymbolicLink(workingTree.resolve("link"), Path.of("Main.java"));
            ObjectId submoduleCommit = ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");
            DirCache dirCache = git.getRepository().lockDirCache();
            DirCacheEditor editor = dirCache.editor();
            editor.add(new DirCacheEditor.PathEdit("lib/module") {

                @Override
                public void apply(DirCacheEntry entry) {
                    entry.setFileMode(FileMode.GITLINK);
                    entry.setObjectId(submoduleCommit);
                }
            });
            editor.commit();
            String commitHash = commitAll(git);

            DeimosSnapshotReader.TextSnapshot snapshot = deimosSnapshotReader.read(git.getRepository(), commitHash);

            assertThat(snapshot.textFiles()).containsOnlyKeys("Main.java");
            // A link is stored as a blob holding its target, a submodule as the commit it pins.
            assertThat(snapshot.otherFiles()).containsOnly(entry("link", blobId("Main.java".getBytes(StandardCharsets.UTF_8))), entry("lib/module", submoduleCommit));
        }
    }

    @Test
    void readReturnsAnEmptySnapshotForACommitThatCannotBeResolved() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            DeimosSnapshotReader.TextSnapshot snapshot = deimosSnapshotReader.read(git.getRepository(), "no-such-commit");

            assertThat(snapshot.textFiles()).isEmpty();
            assertThat(snapshot.otherFiles()).isEmpty();
        }
    }

    private static ObjectId blobId(byte[] content) {
        try (var formatter = new ObjectInserter.Formatter()) {
            return formatter.idFor(Constants.OBJ_BLOB, content);
        }
    }

    private static String commitAll(Git git) throws Exception {
        git.add().addFilepattern(".").call();
        var author = new PersonIdent("Artemis", "artemis@example.com");
        return GitService.commit(git).setMessage("Files").setAuthor(author).setCommitter(author).call().getName();
    }
}
