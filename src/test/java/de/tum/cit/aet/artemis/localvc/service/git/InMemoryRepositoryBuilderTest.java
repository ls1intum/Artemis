package de.tum.cit.aet.artemis.localvc.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;

/**
 * Unit tests for {@link InMemoryRepositoryBuilder}.
 */
class InMemoryRepositoryBuilderTest {

    private static final String BRANCH = "main";

    private static final LocalVCRepositoryUri REPOSITORY_URI = new LocalVCRepositoryUri("https://artemis.tum.de/git/PROJECTKEY/projectkey-student1.git");

    @TempDir
    Path tempDir;

    /**
     * The archive's working tree, index, pack and serialized refs all have to describe one snapshot. Reading the refs
     * only after the working tree has been written leaves a window in which a push moves the branch on, and the archive
     * then claims a tip it never exported - after a force push, one whose objects are not even in the pack.
     */
    @Test
    void shouldExportOneConsistentSnapshotWhenTheBranchMovesDuringTheExport() throws Exception {
        Path source = tempDir.resolve("source");
        ObjectId exportedTip;
        ObjectId tipAfterConcurrentPush;
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch(BRANCH).call()) {
            FileUtils.writeStringToFile(source.resolve("a.txt").toFile(), "first", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            RevCommit exportedCommit = GitService.commit(git).setMessage("exported").setSign(false).call();
            // A commit that is not on the exported branch yet, kept alive by a branch of its own so that the scenario
            // is a plain push rather than a force push, and the assertions cannot pass merely because the object is
            // missing from the archive.
            git.checkout().setCreateBranch(true).setName("incoming").call();
            FileUtils.writeStringToFile(source.resolve("b.txt").toFile(), "second", StandardCharsets.UTF_8);
            git.add().addFilepattern("b.txt").call();
            RevCommit incomingCommit = GitService.commit(git).setMessage("incoming").setSign(false).call();
            git.checkout().setName(BRANCH).call();
            exportedTip = exportedCommit.getId();
            tipAfterConcurrentPush = incomingCommit.getId();
        }

        byte[] archive;
        String gitDir = source.resolve(Constants.DOT_GIT).toString();
        try (Repository repository = new BranchMovingRepository(gitDir, Constants.R_HEADS + BRANCH, tipAfterConcurrentPush)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InMemoryRepositoryBuilder.writeZip(repository, out);
            archive = out.toByteArray();
        }

        Path extracted = tempDir.resolve("extracted");
        ZipTestUtil.extractZip(archive, extracted);

        try (Git exported = Git.open(extracted.toFile())) {
            assertThat(exported.getRepository().resolve(Constants.R_HEADS + BRANCH))
                    .as("the archive records the tip it exported, not the one a concurrent push moved the branch to").isEqualTo(exportedTip);
            assertThat(exported.status().call().isClean()).as("the working tree, the index and HEAD describe the same commit").isTrue();
        }
    }

    /**
     * A zip has no symlink type, so the export materializes a symlink as a plain file holding the link target. The
     * alternative - dropping the entry - would give the extracted repository a working tree that no longer matches the
     * index it ships with, and every git command in it would report the file as deleted.
     */
    @Test
    void shouldMaterializeASymlinkAsAFileHoldingItsTarget() throws Exception {
        Path source = tempDir.resolve("symlink-source");
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch(BRANCH).call()) {
            // JGit only records a symlink as one when core.symlinks is on; it is off by default on some platforms.
            StoredConfig config = git.getRepository().getConfig();
            config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_SYMLINKS, true);
            config.save();

            FileUtils.writeStringToFile(source.resolve("target.txt").toFile(), "the target", StandardCharsets.UTF_8);
            Files.createSymbolicLink(source.resolve("link.txt"), Path.of("target.txt"));
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("a symlink").setSign(false).call();

            assertThat(git.getRepository().readDirCache().getEntry("link.txt").getFileMode()).as("the fixture only tests anything if git recorded a symlink")
                    .isEqualTo(FileMode.SYMLINK);
        }

        byte[] archive;
        String gitDir = source.resolve(Constants.DOT_GIT).toString();
        try (Repository repository = new Repository(gitDir, REPOSITORY_URI)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InMemoryRepositoryBuilder.writeZip(repository, out);
            archive = out.toByteArray();
        }

        assertThat(ZipTestUtil.readEntryAsString(archive, "link.txt")).as("the symlink entry must carry its target as text").isEqualTo("target.txt");
        assertThat(ZipTestUtil.readEntryAsString(archive, "target.txt")).isEqualTo("the target");

        // Content alone is not enough: the index still records mode 120000 for the entry, so unless the archive's config
        // tells git that symlinks are stored as plain files, a freshly extracted repository reports a type change and
        // the "usable repository" the export advertises is dirty before anyone has touched it.
        Path extracted = tempDir.resolve("symlink-extracted");
        ZipTestUtil.extractZip(archive, extracted);
        try (Git exported = Git.open(extracted.toFile())) {
            assertThat(exported.status().call().isClean()).as("a freshly extracted archive containing a symlink must not be dirty").isTrue();
        }
    }

    /**
     * The data export hands each student a directory holding their repository, and used to get there by cloning the
     * bare repository and checking it out. Materializing the same content straight from the bare repository skips the
     * clone entirely, so the archive has to come out indistinguishable from a checkout: history walkable, working tree
     * matching the index, and the executable bit intact.
     */
    @Test
    void shouldMaterializeAUsableRepositoryIntoADirectoryWithoutCloning() throws Exception {
        // Windows has no POSIX view, so the file system cannot carry the executable bit this test asserts on.
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "The file system does not support POSIX permissions");
        Path source = tempDir.resolve("directory-source");
        ObjectId secondCommit;
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch(BRANCH).call()) {
            FileUtils.writeStringToFile(source.resolve("src/Main.java").toFile(), "public class Main {}", StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(source.resolve("gradlew").toFile(), "#!/bin/sh\n", StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(source.resolve("gradlew"), PosixFilePermissions.fromString("rwxr-xr-x"));
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("first").setSign(false).call();
            FileUtils.writeStringToFile(source.resolve("src/Main.java").toFile(), "public class Main { int x; }", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            secondCommit = GitService.commit(git).setMessage("second").setSign(false).call().getId();
        }

        Path target = tempDir.resolve("materialized");
        try (Repository repository = new Repository(source.resolve(Constants.DOT_GIT).toString(), REPOSITORY_URI)) {
            InMemoryRepositoryBuilder.writeToDirectory(repository, target);
        }

        assertThat(target.resolve("src/Main.java")).content(StandardCharsets.UTF_8).isEqualTo("public class Main { int x; }");
        assertThat(Files.isExecutable(target.resolve("gradlew"))).as("the executable bit must survive materialization").isTrue();
        try (Git materialized = Git.open(target.toFile())) {
            assertThat(materialized.getRepository().resolve(Constants.HEAD)).isEqualTo(secondCommit);
            assertThat(materialized.log().call()).as("the history must be walkable").hasSize(2);
            assertThat(materialized.status().call().isClean()).as("the working tree and the index must agree").isTrue();
        }
    }

    /**
     * A participation repository that was created but never pushed to has no commit at all. The clone this export
     * replaced handed the data export an empty working copy for it, so the student still got a directory for that
     * participation; refusing it instead would drop the participation from the export without telling anyone.
     */
    @Test
    void shouldMaterializeARepositoryThatHasNoCommitYet() throws Exception {
        Path source = tempDir.resolve("unborn-source");
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch(BRANCH).call()) {
            assertThat(git.getRepository().resolve(Constants.HEAD)).as("the fixture only tests anything if the branch is unborn").isNull();
        }

        Path target = tempDir.resolve("unborn-materialized");
        try (Repository repository = new Repository(source.resolve(Constants.DOT_GIT).toString(), REPOSITORY_URI)) {
            InMemoryRepositoryBuilder.writeToDirectory(repository, target);
        }

        try (Git materialized = Git.open(target.toFile())) {
            assertThat(materialized.getRepository().getBranch()).as("HEAD must name the branch the empty repository was on").isEqualTo(BRANCH);
            assertThat(materialized.getRepository().resolve(Constants.HEAD)).as("the branch stays unborn, exactly as in the repository that was exported").isNull();
            assertThat(materialized.status().call().isClean()).as("an empty repository is clean").isTrue();
        }
    }

    /**
     * A git tree carries whatever a pushing client wrote into it, and JGit only rejects a {@code ..} entry when
     * {@code receive.fsckObjects} is set, which it is not. Such a name would become an archive entry that escapes the
     * directory it is extracted into, so it has to be refused for every destination rather than only for the directory
     * sink, which is the only one that can compare a resolved path against a root.
     */
    @Test
    void shouldRefuseAnEntryThatLeavesTheRepositoryRootForEveryDestination() throws Exception {
        Path source = tempDir.resolve("escaping-source");
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch(BRANCH).call()) {
            assertThat(git.getRepository().getDirectory()).exists();
        }

        String gitDir = source.resolve(Constants.DOT_GIT).toString();
        try (Repository repository = new Repository(gitDir, REPOSITORY_URI)) {
            commitTreeEscapingTheRoot(repository);

            assertThatExceptionOfType(IOException.class).as("the ZIP export must refuse the entry")
                    .isThrownBy(() -> InMemoryRepositoryBuilder.writeZip(repository, new ByteArrayOutputStream())).withMessageContaining("leaves the repository root");

            Path target = tempDir.resolve("escaping-target");
            assertThatExceptionOfType(IOException.class).as("the directory export must refuse it the same way")
                    .isThrownBy(() -> InMemoryRepositoryBuilder.writeToDirectory(repository, target)).withMessageContaining("leaves the repository root");
            assertThat(tempDir.resolve("escape.txt")).as("nothing may be written next to the export directory").doesNotExist();
        }
    }

    /**
     * A name does not have to contain {@code ..} to land outside the extraction directory. On Windows a leading drive
     * designator makes the entry absolute or relative to that drive, so it has to be refused for the same reason -
     * and the export runs on Linux, where nothing about the name looks unusual.
     */
    @Test
    void shouldRefuseAnEntryThatNamesAWindowsDrive() throws Exception {
        Path source = tempDir.resolve("drive-source");
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch(BRANCH).call()) {
            assertThat(git.getRepository().getDirectory()).exists();
        }

        try (Repository repository = new Repository(source.resolve(Constants.DOT_GIT).toString(), REPOSITORY_URI)) {
            commitTreeWithEntryNamed(repository, "C:escape.txt");

            assertThatExceptionOfType(IOException.class).as("a drive designator must be refused just like ..")
                    .isThrownBy(() -> InMemoryRepositoryBuilder.writeZip(repository, new ByteArrayOutputStream())).withMessageContaining("leaves the repository root");
        }
    }

    /**
     * Commits a tree whose only entry is a directory named {@code ..} holding a file, which {@code TreeWalk} reports as
     * the path {@code ../escape.txt}. Such a tree cannot be produced through the porcelain API, so it is written
     * object by object, the way a crafted push would deliver it.
     */
    private static void commitTreeEscapingTheRoot(Repository repository) throws IOException {
        ObjectId commitId;
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId blob = inserter.insert(Constants.OBJ_BLOB, "escaped".getBytes(StandardCharsets.UTF_8));
            TreeFormatter parentDirectory = new TreeFormatter();
            parentDirectory.append("escape.txt", FileMode.REGULAR_FILE, blob);
            TreeFormatter root = new TreeFormatter();
            root.append("..", FileMode.TREE, inserter.insert(parentDirectory));

            CommitBuilder commitBuilder = new CommitBuilder();
            commitBuilder.setTreeId(inserter.insert(root));
            PersonIdent author = new PersonIdent("Artemis", "artemis@artemis.tum.de");
            commitBuilder.setAuthor(author);
            commitBuilder.setCommitter(author);
            commitBuilder.setMessage("a tree that walks out of the repository");
            commitId = inserter.insert(commitBuilder);
            inserter.flush();
        }
        RefUpdate refUpdate = repository.updateRef(Constants.R_HEADS + BRANCH);
        refUpdate.setNewObjectId(commitId);
        refUpdate.forceUpdate();
    }

    /**
     * Commits a tree holding a single file under the given name, which git itself would never create but a crafted
     * push can deliver.
     */
    private static void commitTreeWithEntryNamed(Repository repository, String name) throws IOException {
        ObjectId commitId;
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId blob = inserter.insert(Constants.OBJ_BLOB, "escaped".getBytes(StandardCharsets.UTF_8));
            TreeFormatter root = new TreeFormatter();
            root.append(name, FileMode.REGULAR_FILE, blob);

            CommitBuilder commitBuilder = new CommitBuilder();
            commitBuilder.setTreeId(inserter.insert(root));
            PersonIdent author = new PersonIdent("Artemis", "artemis@artemis.tum.de");
            commitBuilder.setAuthor(author);
            commitBuilder.setCommitter(author);
            commitBuilder.setMessage("a tree naming a drive");
            commitId = inserter.insert(commitBuilder);
            inserter.flush();
        }
        RefUpdate refUpdate = repository.updateRef(Constants.R_HEADS + BRANCH);
        refUpdate.setNewObjectId(commitId);
        refUpdate.forceUpdate();
    }

    /**
     * A repository that moves a branch the first time a blob is read, which is the moment the export starts
     * materializing the working tree, and therefore exactly the window a concurrent push would land in.
     */
    private static final class BranchMovingRepository extends Repository {

        private final AtomicBoolean moved = new AtomicBoolean();

        private final String refName;

        private final ObjectId newTip;

        private BranchMovingRepository(String gitDir, String refName, ObjectId newTip) throws IOException {
            super(gitDir, REPOSITORY_URI);
            this.refName = refName;
            this.newTip = newTip;
        }

        @Override
        public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
            if (typeHint == Constants.OBJ_BLOB && moved.compareAndSet(false, true)) {
                RefUpdate update = updateRef(refName);
                update.setNewObjectId(newTip);
                update.setForceUpdate(true);
                update.update();
            }
            return super.open(objectId, typeHint);
        }
    }
}
