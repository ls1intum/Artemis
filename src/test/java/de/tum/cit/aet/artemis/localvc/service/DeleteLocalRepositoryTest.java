package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.programming.domain.Repository;

class DeleteLocalRepositoryTest {

    @TempDir
    Path baseDir;

    private GitService gitService;

    @BeforeEach
    void setUp() {
        gitService = new GitService();
    }

    private Repository workingCopy(String name) throws Exception {
        Path workingCopy = Files.createDirectories(baseDir.resolve(name));
        Git.init().setDirectory(workingCopy.toFile()).setInitialBranch("main").call().close();
        FileUtils.write(workingCopy.resolve("README.md").toFile(), "content", StandardCharsets.UTF_8);
        var repository = new Repository(workingCopy.resolve(".git").toString(), new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-" + name));
        ReflectionTestUtils.setField(repository, "localPath", workingCopy);
        return repository;
    }

    @Test
    void deleteLocalRepository_removesTheFolderWithoutLeavingAnythingBehind() throws Exception {
        Repository repository = workingCopy("checkout");

        gitService.deleteLocalRepository(repository);

        try (var remaining = Files.list(baseDir)) {
            assertThat(remaining).as("neither the folder nor a renamed copy of it is left").isEmpty();
        }
    }

    @Test
    void deleteLocalRepository_whenTheContentCannotBeDeleted_stillFreesThePath() throws Exception {
        Repository repository = workingCopy("locked");
        // A folder whose entries cannot be removed, like files that are still open on a network file system
        Path lockedFolder = Files.createDirectories(repository.getLocalPath().resolve("locked"));
        FileUtils.write(lockedFolder.resolve("File.java").toFile(), "class File {}", StandardCharsets.UTF_8);
        Path lockedFolderAfterRename = null;
        assertThat(lockedFolder.toFile().setWritable(false)).isTrue();
        try {
            assertThatCode(() -> gitService.deleteLocalRepository(repository)).doesNotThrowAnyException();

            assertThat(repository.getLocalPath()).as("the path of the repository is free for a new clone").doesNotExist();
        }
        finally {
            try (var remaining = Files.list(baseDir)) {
                lockedFolderAfterRename = remaining.findFirst().map(path -> path.resolve("locked")).orElse(null);
            }
            if (lockedFolderAfterRename != null) {
                lockedFolderAfterRename.toFile().setWritable(true);
            }
            lockedFolder.toFile().setWritable(true);
        }
    }
}
