package de.tum.in.www1.artemis.localvcci;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

class LocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Rule
    public TemporaryFolder remoteRepoTempFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder localRepoTempFolder = new TemporaryFolder();

    @Test
    public void testPushSuccessful() throws IOException, GitAPIException {
        // Set up the temporary Git repository.
        File remoteRepoDir = remoteRepoTempFolder.newFolder("remote-repo.git");
        Git remoteGit = Git.init().setDirectory(remoteRepoDir).setBare(true).call();

        // Clone the repository and make changes.
        File localRepoDir = localRepoTempFolder.newFolder("local-repo");
        Git localGit = Git.cloneRepository().setURI(remoteRepoDir.getAbsolutePath()).setDirectory(localRepoDir).call();

        // Push the changes to the remote repository.
        PushResult pushResult = localGit.push().setRemote("http://localhost:" + port + "/git/remoteRepo.git").call().iterator().next();

        // System.out.println(pushResult.getMessages());
        remoteGit.close();
    }
}
