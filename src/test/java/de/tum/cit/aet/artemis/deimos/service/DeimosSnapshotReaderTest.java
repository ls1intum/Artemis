package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.treewalk.TreeWalk;
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

    @Test
    void readFailsForAFileLargerThanTheFileLimit() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            FileUtils.writeStringToFile(workingTree.resolve("Main.java").toFile(), "x".repeat(101), StandardCharsets.UTF_8);
            String commitHash = commitAll(git);

            assertThat(DeimosSnapshotReader.read(git.getRepository(), commitHash, 101, 1_000, 10).textFiles()).containsOnlyKeys("Main.java");
            assertThatThrownBy(() -> DeimosSnapshotReader.read(git.getRepository(), commitHash, 100, 1_000, 10)).isInstanceOf(IOException.class)
                    .hasMessage("A file of the commit is larger than 100 bytes");
        }
    }

    @Test
    void readFailsWhenTheTextFilesAddUpToMoreThanTheTotalLimit() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            FileUtils.writeStringToFile(workingTree.resolve("A.java").toFile(), "a".repeat(60), StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(workingTree.resolve("B.java").toFile(), "b".repeat(60), StandardCharsets.UTF_8);
            // A binary file is never loaded, so it does not count towards the total.
            FileUtils.writeByteArrayToFile(workingTree.resolve("logo.png").toFile(), new byte[] { (byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
            String commitHash = commitAll(git);

            assertThat(DeimosSnapshotReader.read(git.getRepository(), commitHash, 100, 120, 10).textFiles()).containsOnlyKeys("A.java", "B.java");
            assertThatThrownBy(() -> DeimosSnapshotReader.read(git.getRepository(), commitHash, 100, 119, 10)).isInstanceOf(IOException.class)
                    .hasMessage("The text files of the commit add up to more than 119 bytes");
        }
    }

    @Test
    void readFailsForACommitWithMoreEntriesThanTheEntryLimit() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            for (int file = 0; file < 3; file++) {
                FileUtils.writeStringToFile(workingTree.resolve(String.format(Locale.ROOT, "dir/File%d.java", file)).toFile(), "", StandardCharsets.UTF_8);
            }
            String commitHash = commitAll(git);

            // The directory counts as well: four entries in all.
            assertThat(DeimosSnapshotReader.read(git.getRepository(), commitHash, 100, 100, 4).textFiles()).hasSize(3);
            assertThatThrownBy(() -> DeimosSnapshotReader.read(git.getRepository(), commitHash, 100, 100, 3)).isInstanceOf(IOException.class)
                    .hasMessage("The commit has more than 3 entries");
        }
    }

    @Test
    void readCountsEmptySubtreesTowardsTheEntryLimit() throws Exception {
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call(); ObjectInserter inserter = git.getRepository().newObjectInserter()) {
            // Twenty references to the empty tree hold no file, so a walk that only counts files would never stop.
            ObjectId emptyTree = inserter.insert(new TreeFormatter());
            var rootTree = new TreeFormatter();
            for (int directory = 0; directory < 20; directory++) {
                rootTree.append(String.format(Locale.ROOT, "dir%02d", directory), FileMode.TREE, emptyTree);
            }
            var commit = new CommitBuilder();
            commit.setTreeId(inserter.insert(rootTree));
            var author = new PersonIdent("Artemis", "artemis@example.com");
            commit.setAuthor(author);
            commit.setCommitter(author);
            commit.setMessage("Empty directories");
            String commitHash = inserter.insert(commit).getName();
            inserter.flush();

            assertThat(DeimosSnapshotReader.read(git.getRepository(), commitHash, 100, 100, 20).textFiles()).isEmpty();
            assertThatThrownBy(() -> DeimosSnapshotReader.read(git.getRepository(), commitHash, 100, 100, 19)).isInstanceOf(IOException.class)
                    .hasMessage("The commit has more than 19 entries");
        }
    }

    @Test
    void entryLimitIsNotRecursiveOnItsOwnAndClonesWithAFreshCount() throws Exception {
        var entryLimit = new DeimosSnapshotReader.EntryLimit(1);
        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call(); TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            assertThat(entryLimit.include(treeWalk)).isTrue();
            assertThatThrownBy(() -> entryLimit.include(treeWalk)).isInstanceOf(IOException.class);

            // The walk decides on recursion, and a copy of the filter counts from zero with the same limit.
            assertThat(entryLimit.shouldBeRecursive()).isFalse();
            var copy = entryLimit.clone();
            assertThat(copy).isNotSameAs(entryLimit).isInstanceOf(DeimosSnapshotReader.EntryLimit.class);
            assertThat(copy.include(treeWalk)).isTrue();
            assertThatThrownBy(() -> copy.include(treeWalk)).isInstanceOf(IOException.class);
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
