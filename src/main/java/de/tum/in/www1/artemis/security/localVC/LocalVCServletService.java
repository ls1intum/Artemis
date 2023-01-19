package de.tum.in.www1.artemis.security.localVC;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("localvc")
public class LocalVCServletService {

    private final Logger log = LoggerFactory.getLogger(LocalVCServletService.class);

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    private final Map<String, Repository> repositories = new HashMap<>();

    public LocalVCServletService() {
    }

    /**
     *
     * @param repositoryPath the path of the repository, as parsed out of the URL (everything after /git).
     * @return the opened repository instance.
     */
    public Repository resolveRepository(String repositoryPath) throws RepositoryNotFoundException {
        // Find the local repository depending on the name.
        java.io.File gitDir = new java.io.File(localVCPath + File.separator + repositoryPath);

        log.debug("Path to resolve repository from: {}", gitDir.getPath());
        if (!gitDir.exists()) {
            log.info("Could not find local repository with name {}", repositoryPath);
            throw new RepositoryNotFoundException(repositoryPath);
        }

        Repository repository;

        if (repositories.containsKey(repositoryPath)) {
            log.debug("Retrieving cached local repository {}", repositoryPath);
            repository = repositories.get(repositoryPath);
        }
        else {
            log.debug("Opening local repository {}", repositoryPath);
            try {
                repository = FileRepositoryBuilder.create(gitDir);
                this.repositories.put(repositoryPath, repository);
            }
            catch (IOException e) {
                log.error("Unable to open local repository {}", repositoryPath);
                throw new RepositoryNotFoundException(repositoryPath);
            }
        }

        // Enable pushing without credentials, authentication is handled by the LocalVCPushFilter.
        repository.getConfig().setBoolean("http", null, "receivepack", true);

        // Prevent force-pushes.
        repository.getConfig().setBoolean("receive", null, "denyNonFastForwards", true);

        // Prevent renaming branches.
        repository.getConfig().setBoolean("receive", null, "denyDeletes", true);
        repository.getConfig().setBoolean("receive", null, "denyRenames", true);

        repository.incrementOpen();

        return repository;
    }
}
