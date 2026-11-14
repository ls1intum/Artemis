package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Removes the objects that rejected pushes left in a repository. A push is received completely before it is checked, so the objects of a rejected push stay in the repository
 * without any reference. The repository is marked when a push is rejected. Once no push was rejected in it for a day, the unreferenced objects older than a day are removed. The
 * day of waiting keeps the objects of a push that is still being processed, on this or on another node that uses the same directory.
 */
@Lazy
@Service
@Profile("scheduling & localvc")
public class RejectedPushCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RejectedPushCleanupService.class);

    private static final String MARKER_FILE_NAME = "artemis-rejected-push";

    private static final Duration GRACE_PERIOD = Duration.ofDays(1);

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    /**
     * Marks the repository as one that holds the objects of a rejected push. A repeated call renews the mark, so the cleanup waits for a day without a rejected push.
     *
     * @param repository the repository that received the rejected push
     */
    static void markForCleanup(Repository repository) {
        Path marker = repository.getDirectory().toPath().resolve(MARKER_FILE_NAME);
        try {
            Files.writeString(marker, Instant.now().toString());
        }
        catch (IOException e) {
            log.warn("Could not mark the repository {} for the cleanup of rejected pushes", repository.getDirectory(), e);
        }
    }

    /**
     * Cleans every marked repository whose mark is older than the grace period.
     */
    @Scheduled(cron = "0 15 4 * * *") // execute this every night at 4:15:00 am
    public void cleanup() {
        Instant threshold = Instant.now().minus(GRACE_PERIOD);
        int cleaned = 0;
        try (DirectoryStream<Path> projects = Files.newDirectoryStream(localVCBasePath, Files::isDirectory)) {
            for (Path project : projects) {
                try (DirectoryStream<Path> repositories = Files.newDirectoryStream(project, Files::isDirectory)) {
                    for (Path repositoryPath : repositories) {
                        if (cleanupIfDue(repositoryPath, threshold)) {
                            cleaned++;
                        }
                    }
                }
                catch (IOException e) {
                    log.warn("Could not list the repositories in {}", project, e);
                }
            }
        }
        catch (IOException e) {
            log.warn("Could not list the repositories for the cleanup of rejected pushes", e);
        }
        if (cleaned > 0) {
            log.info("Removed the objects of rejected pushes from {} repositories", cleaned);
        }
    }

    private boolean cleanupIfDue(Path repositoryPath, Instant threshold) {
        Path marker = repositoryPath.resolve(MARKER_FILE_NAME);
        try {
            if (!Files.isRegularFile(marker) || !Files.getLastModifiedTime(marker).toInstant().isBefore(threshold)) {
                return false;
            }
            try (Repository repository = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).setBare().build(); Git git = new Git(repository)) {
                git.gc().setExpire(threshold).call();
            }
            Files.deleteIfExists(marker);
            return true;
        }
        catch (Exception e) {
            // The mark stays, so the next run tries again.
            log.warn("Could not remove the objects of rejected pushes from {}", repositoryPath, e);
            return false;
        }
    }
}
