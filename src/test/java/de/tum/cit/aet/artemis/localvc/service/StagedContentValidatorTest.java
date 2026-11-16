package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.io.FileUtils;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.exception.ExceptionTranslator;

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
        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void acceptedDestinationCanContainObjectsMissingFromTheWorkingCopy(@TempDir Path destinationPath) throws Exception {
        try (Git destination = Git.init().setDirectory(destinationPath.toFile()).setInitialBranch(repository.getBranch()).call()) {
            FileUtils.writeStringToFile(destinationPath.resolve("nested/accepted.txt").toFile(), "Only present at the destination", StandardCharsets.UTF_8);
            destination.add().addFilepattern(".").call();
            var accepted = GitService.commit(destination).setMessage("Accepted remote content").setCommitter("a", "a@example.com").call();
            assertThat(repository.getObjectDatabase().has(accepted.getTree())).isFalse();

            stage("src/Main.java", FileMode.REGULAR_FILE, "class Main {}".getBytes(StandardCharsets.UTF_8));

            assertThatCode(() -> StagedContentValidator.validate(repository, destination.getRepository(), QUOTA)).doesNotThrowAnyException();
        }
    }

    @Test
    void anUnpushedLocalCommitCannotGrandfatherAnOversizedFile(@TempDir Path destinationPath) throws Exception {
        try (Git destination = Git.init().setDirectory(destinationPath.toFile()).setInitialBranch(repository.getBranch()).call()) {
            stage("unaccepted.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
            commit();

            assertThatThrownBy(() -> StagedContentValidator.validate(repository, destination.getRepository(), QUOTA)).isInstanceOf(BadRequestAlertException.class)
                    .hasMessageContaining("unaccepted.bin");
        }
    }

    @Test
    void anUnpushedLocalCommitCannotGrandfatherExcessEntries(@TempDir Path destinationPath) throws Exception {
        try (Git destination = Git.init().setDirectory(destinationPath.toFile()).setInitialBranch(repository.getBranch()).call()) {
            for (int i = 0; i < 3; i++) {
                stage("dir" + i + "/file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
            }
            commit();

            assertThatThrownBy(() -> StagedContentValidator.validate(repository, destination.getRepository(), QUOTA, 5)).isInstanceOf(BadRequestAlertException.class)
                    .hasMessageContaining("files and directories");
        }
    }

    @Test
    void rejectsSymbolicLink() throws Exception {
        stage("link", FileMode.SYMLINK, "../outside".getBytes(StandardCharsets.UTF_8));
        assertRejection("linksNotAllowed", Map.of("path", "link"));
    }

    @Test
    void rejectsSubmodule() throws Exception {
        stage("module", FileMode.GITLINK, new byte[0]);
        assertRejection("linksNotAllowed", Map.of("path", "module"));
    }

    @Test
    void rejectsTooDeepPath() throws Exception {
        String path = "d/".repeat(LocalVCPrePushHook.MAX_TREE_DEPTH + 1) + "file.txt";
        stage(path, FileMode.REGULAR_FILE, new byte[] { 1 });
        assertRejection("pathTooDeep", Map.of("path", "d/".repeat(100) + "...", "limit", LocalVCPrePushHook.MAX_TREE_DEPTH));
    }

    @Test
    void rejectsTooLongPath() throws Exception {
        stage("a".repeat(StagedContentValidator.MAX_PATH_LENGTH + 1), FileMode.REGULAR_FILE, new byte[] { 1 });
        assertRejection("pathTooLong", Map.of("path", "a".repeat(200) + "...", "limit", StagedContentValidator.MAX_PATH_LENGTH));
    }

    @Test
    void rejectsTooLargeFile() throws Exception {
        byte[] content = new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1];
        stage("big.bin", FileMode.REGULAR_FILE, content);
        assertRejection("fileTooLarge", Map.of("path", "big.bin", "limit", LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES));
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
        assertRejection("tooManyEntries", Map.of("limit", LocalVCPrePushHook.MAX_TREE_ENTRIES));
    }

    @Test
    void rejectsGitMetadataNamesWithALocalizableReason() throws Exception {
        stage("git~2/config", FileMode.REGULAR_FILE, new byte[] { 1 });
        assertRejection("pathNotAllowed", Map.of("path", "git~2/config"));
    }

    @Test
    void unreadableIndexHasALocalizableReason() throws Exception {
        FileUtils.writeByteArrayToFile(repository.getIndexFile(), "invalid index".getBytes(StandardCharsets.UTF_8));
        assertRejection("unreadable", Map.of());
    }

    private void assertRejection(String reason, Map<String, Object> parameters) {
        BadRequestAlertException exception = catchThrowableOfType(BadRequestAlertException.class, () -> StagedContentValidator.validate(repository, repository, QUOTA));
        assertThat(exception).isNotNull();
        String errorKey = "stagedContentNotAllowed." + reason;
        ExceptionTranslator translator = new ExceptionTranslator();
        ReflectionTestUtils.setField(translator, "applicationName", "ArtemisApp");
        var response = translator.handleBadRequestAlertException(exception, new ServletWebRequest(new MockHttpServletRequest()));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getFirst("X-ArtemisApp-error")).isEqualTo("error." + errorKey);
        assertThat(response.getBody().getType()).isEqualTo(ErrorConstants.PARAMETERIZED_TYPE);
        assertThat(response.getBody().getProperties()).containsEntry("errorKey", errorKey).containsEntry("message", "error." + errorKey).containsEntry("params", parameters);
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

        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void aChangeToAnExistingOversizedFileIsRefused() throws Exception {
        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        commit();

        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 2]);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void aNewlyAddedOversizedFileIsRefusedAlthoughTheRepositoryHasContent() throws Exception {
        stage("src/Main.java", FileMode.REGULAR_FILE, "class Main {}".getBytes(StandardCharsets.UTF_8));
        commit();

        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, repository, QUOTA)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("big.bin");
    }

    @Test
    void anEmptyCommitIsAccepted() throws Exception {
        stage("big.bin", FileMode.REGULAR_FILE, new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        commit();

        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA)).doesNotThrowAnyException();
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
        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA)).doesNotThrowAnyException();
        commit();
        stageMegabytes("b.bin", 8);
        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA)).doesNotThrowAnyException();
        commit();

        stageMegabytes("c.bin", 8);

        assertRejection("repositoryTooLarge", Map.of("size", 24 * MB, "limit", QUOTA));
    }

    @Test
    void eightMegabytesAddedToAnEighteenMegabyteRepositoryAreRefused() throws Exception {
        stageMegabytes("a.bin", 6);
        stageMegabytes("b.bin", 6);
        stageMegabytes("c.bin", 6);
        commit();

        stageMegabytes("d.bin", 8);

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void anUnrelatedChangeThatDoesNotGrowAnOversizedRepositoryIsAccepted() throws Exception {
        stageMegabytes("a.bin", 9);
        stageMegabytes("b.bin", 9);
        stageMegabytes("c.bin", 9);
        stage("notes.txt", FileMode.REGULAR_FILE, "first".getBytes(StandardCharsets.UTF_8));
        commit();

        stage("notes.txt", FileMode.REGULAR_FILE, "other".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void aCommitThatGrowsAnOversizedRepositoryIsRefused() throws Exception {
        stageMegabytes("a.bin", 9);
        stageMegabytes("b.bin", 9);
        stageMegabytes("c.bin", 9);
        commit();

        stage("notes.txt", FileMode.REGULAR_FILE, "more".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> StagedContentValidator.validate(repository, repository, QUOTA)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void aCommitThatShrinksAnOversizedRepositoryIsAccepted() throws Exception {
        stageMegabytes("a.bin", 9);
        stageMegabytes("b.bin", 9);
        stageMegabytes("c.bin", 9);
        commit();

        unstage("c.bin");

        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA)).doesNotThrowAnyException();
    }

    @Test
    void directoriesCountAsEntriesLikeInThePushCheck() throws Exception {
        // five files in five directories are ten entries
        for (int i = 0; i < 5; i++) {
            stage("dir" + i + "/file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        }

        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA, 10)).doesNotThrowAnyException();
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, repository, QUOTA, 9)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("files and directories");
    }

    @Test
    void aCommitThatDoesNotAddEntriesToATreeAboveTheEntryLimitIsAccepted() throws Exception {
        for (int i = 0; i < 5; i++) {
            stage("dir" + i + "/file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        }
        commit();

        stage("dir0/file.txt", FileMode.REGULAR_FILE, new byte[] { 2 });
        assertThatCode(() -> StagedContentValidator.validate(repository, repository, QUOTA, 9)).doesNotThrowAnyException();

        stage("dir5/file.txt", FileMode.REGULAR_FILE, new byte[] { 1 });
        assertThatThrownBy(() -> StagedContentValidator.validate(repository, repository, QUOTA, 9)).isInstanceOf(BadRequestAlertException.class);
    }
}
