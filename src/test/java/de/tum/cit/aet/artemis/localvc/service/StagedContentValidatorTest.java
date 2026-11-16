package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Unit tests for the checks of the staged content before a commit of the online editor is created.
 */
class StagedContentValidatorTest {

    private static final long MB = 1024L * 1024;

    /** The limit for the size of the files of a repository that applies to students. */
    private static final long QUOTA = 20 * MB;

    @TempDir
    Path repositoryPath;

    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = Git.init().setDirectory(repositoryPath.toFile()).call().getRepository();
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private void stage(String path, FileMode mode, byte[] content) throws IOException {
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId id = mode == FileMode.GITLINK ? ObjectId.fromString("0123456789012345678901234567890123456789") : inserter.insert(Constants.OBJ_BLOB, content);
            inserter.flush();
            DirCache dirCache = repository.lockDirCache();
            DirCacheBuilder builder = dirCache.builder();
            for (int i = 0; i < dirCache.getEntryCount(); i++) {
                if (!dirCache.getEntry(i).getPathString().equals(path)) {
                    builder.add(dirCache.getEntry(i));
                }
            }
            DirCacheEntry entry = new DirCacheEntry(path);
            entry.setFileMode(mode);
            entry.setObjectId(id);
            entry.setLength(content.length);
            builder.add(entry);
            builder.commit();
        }
    }

    @Test
    void acceptsRegularFiles() throws Exception {
        stage("src/Main.java", FileMode.REGULAR_FILE, "class Main {}".getBytes(StandardCharsets.UTF_8));
        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSymbolicLink() throws Exception {
        stage("link", FileMode.SYMLINK, "../outside".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsSubmodule() throws Exception {
        stage("module", FileMode.GITLINK, new byte[0]);
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsTooDeepPath() throws Exception {
        stage("d/".repeat(LocalVCPrePushHook.MAX_TREE_DEPTH + 1) + "file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsTooLongPath() throws Exception {
        stage("a".repeat(StagedContentValidator.MAX_PATH_LENGTH + 1), FileMode.REGULAR_FILE, new byte[] { 1 });
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsTooLargeFile() throws Exception {
        byte[] content = new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1];
        stage("big.bin", FileMode.REGULAR_FILE, content);
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsTooManyFiles() throws Exception {
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId id = inserter.insert(Constants.OBJ_BLOB, new byte[] { 1 });
            inserter.flush();
            DirCache dirCache = repository.lockDirCache();
            DirCacheBuilder builder = dirCache.builder();
            for (int i = 0; i <= LocalVCPrePushHook.MAX_TREE_ENTRIES; i++) {
                DirCacheEntry entry = new DirCacheEntry("f" + i);
                entry.setFileMode(FileMode.REGULAR_FILE);
                entry.setObjectId(id);
                builder.add(entry);
            }
            builder.commit();
        }
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    private void commit() throws Exception {
        try (Git git = new Git(repository)) {
            GitService.commit(git).setMessage("existing content").setAuthor("a", "a@example.com").setCommitter("a", "a@example.com").setSign(false).setAllowEmpty(true).call();
        }
    }

    @Test
    void existingProblematicEntriesDoNotBlockACommitOfAnUnrelatedFile() throws Exception {
        stage("link", FileMode.SYMLINK, "target".getBytes(StandardCharsets.UTF_8));
        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        commit();

        stage("src/Main.java", FileMode.REGULAR_FILE, "class Main {}".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void aChangeToAnExistingOversizedFileIsRefused() throws Exception {
        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        commit();

        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 2]);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void aNewlyAddedOversizedFileIsRefusedAlthoughTheRepositoryHasContent() throws Exception {
        stage("src/Main.java", FileMode.REGULAR_FILE, "class Main {}".getBytes(StandardCharsets.UTF_8));
        commit();

        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("big.bin");
    }

    @Test
    void anEmptyCommitIsAccepted() throws Exception {
        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        commit();

        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA)).doesNotThrowAnyException();
    }

    private void unstage(String path) throws IOException {
        DirCache dirCache = repository.lockDirCache();
        DirCacheBuilder builder = dirCache.builder();
        for (int i = 0; i < dirCache.getEntryCount(); i++) {
            if (!dirCache.getEntry(i).getPathString().equals(path)) {
                builder.add(dirCache.getEntry(i));
            }
        }
        builder.commit();
    }

    private void stageMegabytes(String path, int megabytes) throws IOException {
        stage(path, FileMode.REGULAR_FILE, new byte[(int) (megabytes * MB)]);
    }

    @Test
    void growthAcrossSuccessiveCommitsIsRefusedOnceTheQuotaIsExceeded() throws Exception {
        stageMegabytes("a.bin", 8);
        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA)).doesNotThrowAnyException();
        commit();
        stageMegabytes("b.bin", 8);
        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA)).doesNotThrowAnyException();
        commit();

        stageMegabytes("c.bin", 8);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("limit");
    }

    @Test
    void eightMegabytesAddedToAnEighteenMegabyteRepositoryAreRefused() throws Exception {
        stageMegabytes("a.bin", 6);
        stageMegabytes("b.bin", 6);
        stageMegabytes("c.bin", 6);
        commit();

        stageMegabytes("d.bin", 8);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void anUnrelatedChangeThatDoesNotGrowAnOversizedRepositoryIsAccepted() throws Exception {
        stageMegabytes("a.bin", 9);
        stageMegabytes("b.bin", 9);
        stageMegabytes("c.bin", 9);
        stage("notes.txt", FileMode.REGULAR_FILE, "first".getBytes(StandardCharsets.UTF_8));
        commit();

        stage("notes.txt", FileMode.REGULAR_FILE, "other".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void aCommitThatGrowsAnOversizedRepositoryIsRefused() throws Exception {
        stageMegabytes("a.bin", 9);
        stageMegabytes("b.bin", 9);
        stageMegabytes("c.bin", 9);
        commit();

        stage("notes.txt", FileMode.REGULAR_FILE, "more".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void aCommitThatShrinksAnOversizedRepositoryIsAccepted() throws Exception {
        stageMegabytes("a.bin", 9);
        stageMegabytes("b.bin", 9);
        stageMegabytes("c.bin", 9);
        commit();

        unstage("c.bin");

        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void directoriesCountAsEntriesLikeInThePushCheck() throws Exception {
        // five files in five directories are ten entries
        for (int i = 0; i < 5; i++) {
            stage("dir" + i + "/file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        }

        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA, 10)).doesNotThrowAnyException();
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA, 9)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("files and directories");
    }

    @Test
    void aCommitThatDoesNotAddEntriesToATreeAboveTheEntryLimitIsAccepted() throws Exception {
        for (int i = 0; i < 5; i++) {
            stage("dir" + i + "/file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        }
        commit();

        stage("dir0/file.txt", FileMode.REGULAR_FILE, new byte[] { 2 });
        assertThatCode(() -> StagedContentValidator.validate(repository, QUOTA, 9)).doesNotThrowAnyException();

        stage("dir5/file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA, 9)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectionsCarryTheTranslatableKeyOfTheirReason() throws Exception {
        stage("link", FileMode.SYMLINK, "target".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOfSatisfying(BadRequestAlertException.class,
                e -> assertThat(e.getErrorKey()).isEqualTo(StagedContentValidator.KEY_LINK_NOT_ALLOWED));
        unstage("link");

        stageMegabytes("big.bin", 11);
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOfSatisfying(BadRequestAlertException.class,
                e -> assertThat(e.getErrorKey()).isEqualTo(StagedContentValidator.KEY_FILE_TOO_LARGE));
        unstage("big.bin");

        stageMegabytes("a.bin", 9);
        stageMegabytes("b.bin", 9);
        stageMegabytes("c.bin", 9);
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, QUOTA)).isInstanceOfSatisfying(BadRequestAlertException.class,
                e -> assertThat(e.getErrorKey()).isEqualTo(StagedContentValidator.KEY_TOO_LARGE));
    }
}
