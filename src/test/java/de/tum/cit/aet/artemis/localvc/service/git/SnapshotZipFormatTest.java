package de.tum.cit.aet.artemis.localvc.service.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.junit.jupiter.api.Test;

class SnapshotZipFormatTest {

    @Test
    void snapshotLeavesOutEntriesNamedLikeTheMetadataDirectory() throws Exception {
        try (InMemoryRepository repository = new InMemoryRepository(new DfsRepositoryDescription("snapshot"))) {
            ObjectId treeId;
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                ObjectId blob = inserter.insert(Constants.OBJ_BLOB, "content".getBytes(StandardCharsets.UTF_8));
                TreeFormatter nested = new TreeFormatter();
                nested.append("config", FileMode.REGULAR_FILE, blob);
                ObjectId nestedId = inserter.insert(nested);
                TreeFormatter root = new TreeFormatter();
                root.append(".GIT", FileMode.TREE, nestedId);
                root.append("Main.java", FileMode.REGULAR_FILE, blob);
                treeId = inserter.insert(root);
                inserter.flush();
            }
            ObjectId commitId;
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                CommitBuilder commit = new CommitBuilder();
                commit.setTreeId(treeId);
                commit.setAuthor(new PersonIdent("a", "a@example.org"));
                commit.setCommitter(new PersonIdent("a", "a@example.org"));
                commit.setMessage("m");
                commitId = inserter.insert(commit);
                inserter.flush();
            }

            ArchiveCommand.registerFormat("snapshot-test", new SnapshotZipFormat());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (Git git = new Git(repository)) {
                git.archive().setFormat("snapshot-test").setTree(commitId).setOutputStream(out).call();
            }
            finally {
                ArchiveCommand.unregisterFormat("snapshot-test");
            }

            List<String> names = new ArrayList<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    names.add(entry.getName());
                }
            }
            assertThat(names).containsExactly("Main.java");
        }
    }
}
