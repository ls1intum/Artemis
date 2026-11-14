package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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
 * <p>
 * A garbage collection first turns unreferenced objects of old packs into loose objects with a recent modification time, and only a later run removes them. The repository is
 * therefore cleaned in two passes, a day apart, before the mark is dropped. A cleanup claims the mark by renaming it, so a rejected push that renews the mark while the
 * cleanup runs creates a new mark that the cleanup does not remove.
 */
@Lazy
@Service
@Profile("scheduling & localvc")
public class RejectedPushCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RejectedPushCleanupService.class);

    private static final String MARKER_FILE_NAME = "artemis-rejected-push";

    private static final String CLAIM_PREFIX = MARKER_FILE_NAME + ".claimed-";

    /** The content of a mark that no cleanup has processed yet. */
    private static final String FIRST_PASS = "0";

    /** The content of a mark after a first cleanup, which a second cleanup removes together with the objects that the first one made loose. */
    private static final String SECOND_PASS = "1";

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
            Files.writeString(marker, FIRST_PASS);
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
        Path claim = null;
        try {
            claim = claim(repositoryPath, marker, threshold);
            if (claim == null) {
                return false;
            }
            String pass = Files.readString(claim).trim();
            try (Repository repository = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).setBare().build(); Git git = new Git(repository)) {
                git.gc().setExpire(threshold).call();
            }
            if (!SECOND_PASS.equals(pass)) {
                // Objects that the collection made loose are only removed by a later run. A mark written meanwhile by a rejected push is a newer generation and stays.
                try {
                    Files.writeString(marker, SECOND_PASS, StandardOpenOption.CREATE_NEW);
                }
                catch (FileAlreadyExistsException e) {
                    log.debug("The repository {} was marked again during the cleanup", repositoryPath);
                }
            }
            Files.deleteIfExists(claim);
            return true;
        }
        catch (Exception e) {
            // The mark is restored, so the next run tries again.
            log.warn("Could not remove the objects of rejected pushes from {}", repositoryPath, e);
            restore(marker, claim);
            return false;
        }
    }

    /**
     * Takes the mark of the repository by renaming it, which only one cleanup can do. A claim that an earlier run left behind, for example because the server stopped during the
     * collection, is taken again once it is older than the threshold.
     *
     * @return the claimed file, or null if the repository has no mark that is due
     */
    private Path claim(Path repositoryPath, Path marker, Instant threshold) throws IOException {
        if (Files.isRegularFile(marker) && Files.getLastModifiedTime(marker).toInstant().isBefore(threshold)) {
            Path claim = repositoryPath.resolve(CLAIM_PREFIX + UUID.randomUUID());
            try {
                Files.move(marker, claim, StandardCopyOption.ATOMIC_MOVE);
                return claim;
            }
            catch (NoSuchFileException e) {
                // another node took it first
                return null;
            }
        }
        try (DirectoryStream<Path> leftovers = Files.newDirectoryStream(repositoryPath, CLAIM_PREFIX + "*")) {
            for (Path leftover : leftovers) {
                if (Files.getLastModifiedTime(leftover).toInstant().isBefore(threshold)) {
                    Path claim = repositoryPath.resolve(CLAIM_PREFIX + UUID.randomUUID());
                    try {
                        Files.move(leftover, claim, StandardCopyOption.ATOMIC_MOVE);
                        return claim;
                    }
                    catch (NoSuchFileException e) {
                        // another node took it first
                    }
                }
            }
        }
        return null;
    }

    private void restore(Path marker, Path claim) {
        if (claim == null || !Files.exists(claim)) {
            return;
        }
        try {
            // Without REPLACE_EXISTING a newer mark of a rejected push is kept, and then the claim is no longer needed
            Files.move(claim, marker);
        }
        catch (FileAlreadyExistsException e) {
            try {
                Files.deleteIfExists(claim);
            }
            catch (IOException deleteFailure) {
                log.debug("Could not remove the claim {}", claim, deleteFailure);
            }
        }
        catch (IOException e) {
            log.debug("Could not restore the mark of {}", claim, e);
        }
    }
}
