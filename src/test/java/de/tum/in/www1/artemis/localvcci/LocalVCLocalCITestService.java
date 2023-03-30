package de.tum.in.www1.artemis.localvcci;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalVCLocalCITestService {

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    public Repository createGitRepositoryWithInitialPush(File tempRemoteRepoFolder) {
        try {
            Git git = Git.init().setDirectory(tempRemoteRepoFolder).setBare(true).call();

            // Change the default branch to the Artemis default branch name.
            Repository repository = git.getRepository();
            RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
            refUpdate.setForceUpdate(true);
            refUpdate.link("refs/heads/" + defaultBranch);

            // Push some files to the repository.

            git.close();

            return repository;
        }
        catch (IOException | GitAPIException e) {
            throw new RuntimeException("Could not create temp git repository", e);
        }
    }
}
