package de.tum.in.www1.artemis.programmingexercise;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

class ProgrammingExerciseLocalCILocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexloc";

    File tempRemoteRepoFolder;

    File tempLocalRepoFolder;

    Repository tempRepo;

    @AfterEach
    public void tearDown() throws IOException {
        tempRepo.close();
        FileUtils.deleteDirectory(tempRemoteRepoFolder);
        FileUtils.deleteDirectory(tempLocalRepoFolder);
    }

    @Test
    public void testPushSuccessful() throws IOException, GitAPIException {
        // String environmentPort = environment.getProperty("local.server.port");
        // int port = 0;
        // if (environmentPort != null) {
        // port = Integer.parseInt(environment.getProperty("local.server.port"));
        // }
        // Set up the temporary Git repository.
        tempRepo = createTempGitRepository();

        // Clone the repository and make changes.
        tempLocalRepoFolder = Files.createTempDirectory("localRepo").toFile();
        // Git git = Git.cloneRepository().setURI(tempRemoteRepoFolder.getAbsolutePath()).setDirectory(tempLocalRepoFolder).call();

        // TODO: Make changes to the repository.

        // Push the changes to the remote repository.
        // PushResult pushResult = git.push().setRemote("http://localhost:" + port + "/git/remoteRepo.git").call().iterator().next();

        // System.out.println(pushResult.getMessages());
        // git.close();
    }

    private Repository createTempGitRepository() {
        try {
            tempRemoteRepoFolder = Files.createTempDirectory("remoteRepo").toFile();
            tempRemoteRepoFolder.deleteOnExit();
            RepositoryBuilder repositoryBuilder = new RepositoryBuilder();
            return repositoryBuilder.setGitDir(tempRemoteRepoFolder).setBare().build();
        }
        catch (IOException e) {
            throw new RuntimeException("Could not create temp git repository", e);
        }
    }
}
