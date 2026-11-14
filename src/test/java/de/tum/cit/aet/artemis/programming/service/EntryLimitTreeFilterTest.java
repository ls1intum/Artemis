package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryLimitTreeFilterTest {

    @TempDir
    Path repositoryPath;

    private ObjectId treeWithFiles(Repository repository, int files) throws Exception {
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId blob = inserter.insert(Constants.OBJ_BLOB, "x".getBytes(StandardCharsets.UTF_8));
            TreeFormatter formatter = new TreeFormatter();
            for (int index = 0; index < files; index++) {
                formatter.append("f%03d".formatted(index), FileMode.REGULAR_FILE, blob);
            }
            ObjectId tree = inserter.insert(formatter);
            inserter.flush();
            return tree;
        }
    }

    private int walk(Repository repository, ObjectId tree, TreeFilter filter) throws Exception {
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(filter);
            int returned = 0;
            while (treeWalk.next()) {
                returned++;
            }
            return returned;
        }
    }

    @Test
    void walkWithinTheLimitReturnsTheEntriesOfTheDelegate() throws Exception {
        try (Repository repository = Git.init().setDirectory(repositoryPath.toFile()).setBare(true).call().getRepository()) {
            ObjectId tree = treeWithFiles(repository, 5);

            assertThat(walk(repository, tree, new EntryLimitTreeFilter(TreeFilter.ALL, 5))).isEqualTo(5);
            assertThat(walk(repository, tree, new EntryLimitTreeFilter(PathFilter.create("f002"), 5))).isEqualTo(1);
        }
    }

    @Test
    void walkBeyondTheLimitFailsEvenWhenTheDelegateExcludesTheEntries() throws Exception {
        try (Repository repository = Git.init().setDirectory(repositoryPath.toFile()).setBare(true).call().getRepository()) {
            ObjectId tree = treeWithFiles(repository, 6);

            assertThatIOException().isThrownBy(() -> walk(repository, tree, new EntryLimitTreeFilter(TreeFilter.ALL, 5))).withMessageContaining("more than 5 entries");
            assertThatIOException().isThrownBy(() -> walk(repository, tree, new EntryLimitTreeFilter(PathFilter.create("f000"), 5))).withMessageContaining("more than 5 entries");
        }
    }
}
