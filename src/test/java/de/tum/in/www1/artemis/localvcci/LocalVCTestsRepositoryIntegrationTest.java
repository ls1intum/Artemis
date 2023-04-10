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
import de.tum.in.www1.artemis.util.LocalRepository;

public class LocalVCTestsRepositoryIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    // ---- Repository handles ----
    private Path remoteTestsRepositoryFolder;

    private Git remoteTestsGit;

    private Path localTestsRepositoryFolder;

    private Git localTestsGit;

    private String testsRepositorySlug;

    @BeforeEach
    void initRepository() throws IOException, GitAPIException, URISyntaxException {
        testsRepositorySlug = projectKey1.toLowerCase() + "-tests";
        remoteTestsRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, testsRepositorySlug);
        LocalRepository testsRepository = new LocalRepository(defaultBranch);
        testsRepository.configureRepos("localTests", remoteTestsRepositoryFolder);
        remoteTestsGit = testsRepository.originGit;
        localTestsRepositoryFolder = testsRepository.localRepoFile.toPath();
        localTestsGit = testsRepository.localGit;
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

    @Test
    void testFetchPush_testsRepository() {

    }
}
