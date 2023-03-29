package de.tum.in.www1.artemis.localvc;

import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

class LocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    public void testPushSuccessful() throws IOException, GitAPIException {
        // String environmentPort = environment.getProperty("local.server.port");
        // int port = 0;
        // if (environmentPort != null) {
        // port = Integer.parseInt(environment.getProperty("local.server.port"));
        // }
        // Set up the temporary Git repository.
        createGitRepositoryWithInitialPush();

        // Clone the repository and make changes.
        tempLocalRepoFolder = Files.createTempDirectory("localRepo").toFile();
        // Git git = Git.cloneRepository().setURI(tempRemoteRepoFolder.getAbsolutePath()).setDirectory(tempLocalRepoFolder).call();

        // Push the changes to the remote repository.
        // PushResult pushResult = git.push().setRemote("http://localhost:" + port + "/git/remoteRepo.git").call().iterator().next();

        // System.out.println(pushResult.getMessages());
        // git.close();
    }
}
