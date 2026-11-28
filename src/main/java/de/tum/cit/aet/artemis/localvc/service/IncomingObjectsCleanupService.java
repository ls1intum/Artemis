package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Removes the places of pushes that were left behind. The objects of a push are stored in a place of their own, which the push removes when it ends, whether it was accepted or
 * rejected. Only a push that is cut off by a restart of the server leaves its place, and this job removes such places once they are a day old, which no push lasts.
 */
@Lazy
@Service
@Profile("scheduling & localvc")
public class IncomingObjectsCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IncomingObjectsCleanupService.class);

    private static final Duration MIN_AGE = Duration.ofDays(1);

    private final Path localVCBasePath;

    public IncomingObjectsCleanupService(@Value("${artemis.version-control.local-vcs-repo-path}") Path localVCBasePath) {
        this.localVCBasePath = localVCBasePath;
    }

    /**
     * Removes every place of a push that is older than a day.
     */
    @Scheduled(cron = "0 15 4 * * *") // execute this every night at 4:15:00 am
    public void cleanup() {
        Instant threshold = Instant.now().minus(MIN_AGE);
        int removed = 0;
        try (DirectoryStream<Path> projects = Files.newDirectoryStream(localVCBasePath, Files::isDirectory)) {
            for (Path project : projects) {
                try (DirectoryStream<Path> repositories = Files.newDirectoryStream(project, Files::isDirectory)) {
                    for (Path repository : repositories) {
                        removed += removeOldPlaces(repository, threshold);
                    }
                }
                catch (IOException e) {
                    log.warn("Could not list the repositories in {}", project, e);
                }
            }
        }
        catch (IOException e) {
            log.warn("Could not list the repositories for the cleanup of the places of pushes", e);
        }
        if (removed > 0) {
            log.info("Removed {} places of pushes that were left behind", removed);
        }
    }

    private int removeOldPlaces(Path repository, Instant threshold) {
        int removed = 0;
        try (DirectoryStream<Path> places = Files.newDirectoryStream(repository, PushQuarantine.DIRECTORY_PREFIX + "*")) {
            for (Path place : places) {
                if (Files.isDirectory(place) && Files.getLastModifiedTime(place).toInstant().isBefore(threshold)) {
                    FileUtils.deleteQuietly(place.toFile());
                    removed++;
                }
            }
        }
        catch (IOException e) {
            log.warn("Could not clean up the places of pushes in {}", repository, e);
        }
        return removed;
    }
}
