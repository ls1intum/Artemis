package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.Repository;

class SecurityFrameworkRestorePointTest {

    @TempDir
    private Path directory;

    @Test
    void reportsAHistoryThatCannotBeReadWhileItIsWalked() throws Exception {
        RevCommit first;
        try (Git git = Git.init().setDirectory(directory.toFile()).setInitialBranch("main").call()) {
            first = commitFile(git, "first");
            commitFile(git, "second");
            commitFile(git, "third");
        }
        // The walk reads the head and its parent before it reaches the first commit, which is gone.
        String name = first.name();
        Files.delete(directory.resolve(".git").resolve("objects").resolve(name.substring(0, 2)).resolve(name.substring(2)));

        try (Repository repository = new Repository(directory.resolve(".git").toString(), null)) {
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> SecurityFrameworkRestorePoint.annotationStatesBeforeLastDeactivation(repository))
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityFrameworkRestorePointUnreadable"));
        }
    }

    private RevCommit commitFile(Git git, String content) throws Exception {
        File file = directory.resolve("notes.txt").toFile();
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
        git.add().addFilepattern(".").call();
        return GitService.commit(git).setMessage(content).setAuthor("Artemis", "artemis@example.org").call();
    }
}
