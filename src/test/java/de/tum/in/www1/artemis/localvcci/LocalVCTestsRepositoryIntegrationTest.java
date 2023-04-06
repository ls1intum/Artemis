package de.tum.in.www1.artemis.localvcci;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

public class LocalVCTestsRepositoryIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    // ---- Repository handles ----
    protected Path remoteTestsRepositoryFolder;

    protected Git remoteTestsGit;

    protected Path localTestsRepositoryFolder;

    protected Git localTestsGit;

    @BeforeEach
    void initRepository() throws IOException, GitAPIException, URISyntaxException {
        final String testsRepoName = projectKey1.toLowerCase() + "-tests";
        remoteTestsRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, testsRepoName);
        remoteTestsGit = localVCLocalCITestService.createGitRepository(remoteTestsRepositoryFolder);

        // Clone the remote tests repository into a local folder.
        localTestsRepositoryFolder = Files.createTempDirectory("localTests");
        localTestsGit = Git.cloneRepository().setURI(remoteTestsRepositoryFolder.toString()).setDirectory(localTestsRepositoryFolder.toFile()).call();
    }

    @AfterEach
    void removeRepositories() throws IOException {
        if (remoteTestsGit != null) {
            remoteTestsGit.close();
        }
        if (localTestsGit != null) {
            localTestsGit.close();
        }
        if (remoteTestsRepositoryFolder != null && Files.exists(remoteTestsRepositoryFolder)) {
            FileUtils.deleteDirectory(remoteTestsRepositoryFolder.toFile());
        }
        if (localTestsRepositoryFolder != null && Files.exists(localTestsRepositoryFolder)) {
            FileUtils.deleteDirectory(localTestsRepositoryFolder.toFile());
        }
    }

    // ---- Tests for the tests repository ----

    @Test
    void testFetch_testsRepository_student() {
        // The tests repository can only be fetched by users that are at least teaching assistants in the course.
    }

    @Test
    void testPush_testsRepository_student() {
        // The tests repository can only be pushed to by users that are at least teaching assistants in the course.
    }

    @Test
    void testFetch_testsRepository_teachingAssistant() {
        // Should be successful
    }

    @Test
    void testFetch_testsRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testPush_testsRepository_teachingAssistant_noParticipation() {

    }
}
