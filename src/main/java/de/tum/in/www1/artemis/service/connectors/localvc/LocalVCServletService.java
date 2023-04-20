package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Contains methods used to create the local VC servlet.
 */
@Service
@Profile("localvc")
public class LocalVCServletService {

    private final Logger log = LoggerFactory.getLogger(LocalVCServletService.class);

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    // Cache the retrieved repositories for quicker access.
    // The resolveRepository method is called multiple times per request.
    private final Map<String, Repository> repositories = new HashMap<>();

    /**
     *
     * @param repositoryPath the path of the repository, as parsed out of the URL (everything after /git).
     * @return the opened repository instance.
     * @throws RepositoryNotFoundException if the repository could not be found.
     */
    public Repository resolveRepository(String repositoryPath) throws RepositoryNotFoundException {
        // Find the local repository depending on the name.
        Path repositoryDir = Paths.get(localVCPath, repositoryPath);

        log.debug("Path to resolve repository from: {}", repositoryDir);
        if (!Files.exists(repositoryDir)) {
            log.info("Could not find local repository with name {}", repositoryPath);
            throw new RepositoryNotFoundException(repositoryPath);
        }

        if (repositories.containsKey(repositoryPath)) {
            log.debug("Retrieving cached local repository {}", repositoryPath);
            Repository repository = repositories.get(repositoryPath);
            repository.incrementOpen();
            return repository;
        }
        else {
            log.debug("Opening local repository {}", repositoryPath);
            try (Repository repository = FileRepositoryBuilder.create(repositoryDir.toFile())) {
                // Enable pushing without credentials, authentication is handled by the LocalVCPushFilter.
                repository.getConfig().setBoolean("http", null, "receivepack", true);

                this.repositories.put(repositoryPath, repository);
                repository.incrementOpen();
                return repository;
            }
            catch (IOException e) {
                log.error("Unable to open local repository {}", repositoryPath);
                throw new RepositoryNotFoundException(repositoryPath, e);
            }
        }
    }
}
