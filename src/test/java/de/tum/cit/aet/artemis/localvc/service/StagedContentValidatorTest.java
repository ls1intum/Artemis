package de.tum.cit.aet.artemis.localvc.service;

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
        assertThatCode(() -> StagedContentValidator.validate(repository)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSymbolicLink() throws Exception {
        stage("link", FileMode.SYMLINK, "../outside".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsSubmodule() throws Exception {
        stage("module", FileMode.GITLINK, new byte[0]);
        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsTooDeepPath() throws Exception {
        stage("d/".repeat(LocalVCPrePushHook.MAX_TREE_DEPTH + 1) + "file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsTooLongPath() throws Exception {
        stage("a".repeat(StagedContentValidator.MAX_PATH_LENGTH + 1), FileMode.REGULAR_FILE, new byte[] { 1 });
        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsTooLargeFile() throws Exception {
        byte[] content = new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1];
        stage("big.bin", FileMode.REGULAR_FILE, content);
        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class);
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
        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class);
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

        assertThatCode(() -> StagedContentValidator.validate(repository)).doesNotThrowAnyException();
    }

    @Test
    void aChangeToAnExistingOversizedFileIsRefused() throws Exception {
        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        commit();

        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 2]);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void aNewlyAddedOversizedFileIsRefusedAlthoughTheRepositoryHasContent() throws Exception {
        stage("src/Main.java", FileMode.REGULAR_FILE, "class Main {}".getBytes(StandardCharsets.UTF_8));
        commit();

        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("big.bin");
    }

    @Test
    void anEmptyCommitIsAccepted() throws Exception {
        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        commit();

        assertThatCode(() -> StagedContentValidator.validate(repository)).doesNotThrowAnyException();
    }
}
