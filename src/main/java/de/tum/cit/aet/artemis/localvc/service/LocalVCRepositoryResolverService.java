package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

/**
 * Finds the bare repositories of the local version control on disk and reads what a git request or a repository path says about them.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCRepositoryResolverService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCRepositoryResolverService.class);

    /** A carriage return or a line feed, replaced so that a logged path cannot forge a second log line. */
    private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n]");

    /** The git service suffix of a request path, which is not part of the repository path. */
    private static final Pattern GIT_SERVICE_SUFFIX = Pattern.compile("/(info/refs|git-(upload|receive)-pack)$");

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    /**
     * Resolves the repository for the given path by creating a JGit repository and opening the local repository.
     * <p>
     * The returned {@link Repository} remains open after this method returns.
     * It is the caller's responsibility to close it when no longer needed.
     * <strong>Do not</strong> use try-with-resources inside this method, as that would close the repository
     * before the caller can use it.
     *
     * @param repositoryPath the path of the repository, as parsed out of the URL (everything after /git).
     * @return the opened repository instance.
     * @throws RepositoryNotFoundException if the repository could not be found.
     */
    public Repository resolveRepository(String repositoryPath) throws RepositoryNotFoundException {

        long timeNanoStart = System.nanoTime();
        // Sanitize once for all log statements to prevent CRLF injection
        String sanitizedPath = LINE_BREAK.matcher(repositoryPath).replaceAll("_");

        // Find the local repository depending on the name.
        Path normalizedBasePath = localVCBasePath.normalize();
        Path repositoryDir = normalizedBasePath.resolve(repositoryPath).normalize();

        // Prevent path traversal attacks by ensuring the resolved path stays within the base path
        if (!repositoryDir.startsWith(normalizedBasePath)) {
            log.error("Blocked path traversal attempt for repository path: {}", sanitizedPath);
            throw new RepositoryNotFoundException(repositoryPath);
        }

        log.debug("Path to resolve repository from: {}", sanitizedPath);
        if (!Files.exists(repositoryDir)) {
            // A client asking for a path that does not exist is not a server error: students' tools poll the predictable URL of their repository before they have started
            // the exercise. A storage that is gone or unreadable is one. Files.exists is also false then, so the repository only counts as missing for the client if the
            // base path is a directory and the repository is known not to exist.
            if (Files.isDirectory(normalizedBasePath) && Files.notExists(repositoryDir)) {
                log.warn("Could not find local repository with name {}", sanitizedPath);
            }
            else {
                log.error("Could not find local repository with name {}, the storage of the repositories may be unavailable", sanitizedPath);
            }
            throw new RepositoryNotFoundException(repositoryPath);
        }

        // After confirming the path exists, resolve symlinks and verify the real path is still within the base
        try {
            Path realBasePath = localVCBasePath.toRealPath();
            Path realRepositoryDir = repositoryDir.toRealPath();
            if (!realRepositoryDir.startsWith(realBasePath)) {
                log.error("Blocked symlink-based path traversal for repository path: {}", sanitizedPath);
                throw new RepositoryNotFoundException(repositoryPath);
            }
        }
        catch (IOException e) {
            throw new RepositoryNotFoundException(repositoryPath, e);
        }

        log.debug("Opening local repository {}", sanitizedPath);
        try {
            Repository repository = FileRepositoryBuilder.create(repositoryDir.toFile());
            // Enable pushing without credentials, authentication is handled by the LocalVCPushFilter.
            repository.getConfig().setBoolean("http", null, "receivepack", true);

            if (log.isDebugEnabled()) {
                log.debug("Resolving repository for repository {} took {}", sanitizedPath, TimeLogUtil.formatDurationFrom(timeNanoStart));
            }
            return repository;
        }
        catch (IOException e) {
            log.error("Unable to open local repository {}", sanitizedPath);
            throw new RepositoryNotFoundException(repositoryPath, e);
        }
    }

    /**
     * Parses the repository a git request addresses, ignoring the git service suffix of its path.
     *
     * @param request the git request
     * @return the repository URI
     */
    public LocalVCRepositoryUri parseRepositoryUri(HttpServletRequest request) {
        String path = request.getRequestURI();
        String normalizedPath = GIT_SERVICE_SUFFIX.matcher(path).replaceFirst("");
        return new LocalVCRepositoryUri(localVCBaseUri, Path.of(normalizedPath));
    }

    /**
     * Parses the repository a path points to.
     *
     * @param repositoryPath the path of the repository, below the base path of the repositories
     * @return the repository URI
     */
    public LocalVCRepositoryUri parseRepositoryUri(Path repositoryPath) {
        return new LocalVCRepositoryUri(localVCBaseUri, repositoryPath);
    }

    /**
     * Retrieves the latest commit hash from the given repository.
     *
     * @param localVCRepositoryUri The {@link LocalVCRepositoryUri} representing the repository location.
     * @return The latest commit hash as a string, or an empty string if retrieval fails.
     */
    public String getCommitHash(LocalVCRepositoryUri localVCRepositoryUri) {
        try {
            String repositoryPath = localVCRepositoryUri.getRelativeRepositoryPath().toString();
            try (Repository repository = resolveRepository(repositoryPath)) {
                return getLatestCommitHash(repository);
            }
        }
        catch (GitAPIException | RepositoryNotFoundException e) {
            log.warn("Failed to obtain commit hash for repository {}. Error: {}", localVCRepositoryUri.getRelativeRepositoryPath(), e.getMessage());
        }
        return "";
    }

    /**
     * Reads the hash of the newest commit of a repository.
     *
     * @param repository the repository to read
     * @return the hash of the latest commit
     * @throws GitAPIException if the log of the repository cannot be read
     */
    public String getLatestCommitHash(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            RevCommit latestCommit = git.log().setMaxCount(1).call().iterator().next();
            return latestCommit.getName();
        }
    }
}
