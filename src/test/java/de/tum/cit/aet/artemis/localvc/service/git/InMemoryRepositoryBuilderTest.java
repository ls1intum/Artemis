package de.tum.cit.aet.artemis.localvc.service.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
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
