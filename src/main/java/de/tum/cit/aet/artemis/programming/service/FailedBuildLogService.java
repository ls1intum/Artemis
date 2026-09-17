package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/**
 * Stores the build logs of failed builds on disk, one file per programming submission.
 * <p>
 * This is deliberately <b>not</b> the store behind {@code artemis.build-logs-path}. That one keeps the complete log of every build job for
 * {@code artemis.continuous-integration.build-log.file-expiry-days} days, is keyed by build job id, and is only ever streamed to the browser as a download. This one keeps the
 * logs of <b>failed</b> builds for a year, is keyed by submission id because that is all its readers have, and is parsed back into entries to serve the build output panel,
 * Iris and Hyperion. Sharing one store would force one retention period onto both use cases.
 * <p>
 * <b>Layout.</b> {@code <root>/<submissionId / 10000>/<submissionId>.log}. The bucket keeps a directory to roughly ten thousand files without needing anything but the
 * submission id to find a file again, which matters because none of the readers have the course or the exercise at hand.
 * <p>
 * <b>Format.</b> One entry per line, {@code <ISO-8601 timestamp>\t<log>}. That is the shape the build agent already produces, and it is only unambiguous because
 * {@link #splitIntoLines} guarantees no stored log value contains a line break: an entry whose log spans several lines is written as several entries sharing its timestamp.
 * On production data 31% of entries need that, overwhelmingly composed error messages carrying a stack trace. The alternative, keeping multi-line entries intact, needs an
 * escaping or framing layer for a difference no reader can observe - the build output panel groups consecutive entries and prints a timestamp only when it changes, so
 * several lines under one timestamp render exactly as one entry containing those lines did. A tab inside a log value is harmless because only the first one separates.
 * {@code BuildLogParseUtils} applies the same rule to Jenkins logs, for the same reason.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class FailedBuildLogService {

    private static final Logger log = LoggerFactory.getLogger(FailedBuildLogService.class);

    /**
     * How many submissions share a bucket directory. Purely a filesystem concern; changing it makes previously written files unreachable, so it is not configurable.
     */
    private static final long SUBMISSIONS_PER_BUCKET = 10_000;

    private static final char SEPARATOR = '\t';

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ProfileService profileService;

    @Value("${artemis.failed-build-logs-path:./failed-build-logs}")
    private Path failedBuildLogsPath;

    @Value("${artemis.continuous-integration.build-log.failed-build-retention-days:365}")
    private int retentionDays;

    public FailedBuildLogService(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Writes the build logs of a failed build, replacing whatever was stored for this submission before.
     * <p>
     * Multi-line entries are split into one entry per line, all keeping the timestamp of the entry they came from, so that no stored value contains a line break.
     *
     * @param submissionId the programming submission the logs belong to
     * @param buildLogs    the entries to store
     * @return the entries as they were stored, which is what a subsequent read returns
     * @throws UncheckedIOException if the logs could not be written. The caller has to know, because that is what decides whether the rows this file replaces may go.
     */
    public List<BuildLogEntry> saveBuildLogs(long submissionId, List<BuildLogEntry> buildLogs) {
        List<BuildLogEntry> normalized = splitIntoLines(buildLogs);
        Path logPath = pathFor(submissionId);

        StringBuilder content = new StringBuilder();
        for (BuildLogEntry entry : normalized) {
            content.append(entry.getTime() == null ? "" : TIMESTAMP_FORMAT.format(entry.getTime())).append(SEPARATOR).append(entry.getLog()).append('\n');
        }

        Path temporaryPath = null;
        try {
            Files.createDirectories(logPath.getParent());
            // Written beside the target and moved into place, so that a reader never sees half a file: this store is parsed rather than streamed, and a build that is rebuilt
            // while its logs are being read would otherwise truncate the file under the reader. The temporary name is unique per call rather than derived from the submission,
            // because two builds of one submission finishing together would otherwise share it and move each other's half of it into place.
            temporaryPath = Files.createTempFile(logPath.getParent(), submissionId + "-", ".tmp");
            Files.writeString(temporaryPath, content.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporaryPath, logPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e) {
                // A shared folder is usually NFS and not every export supports an atomic rename. A plain replace is still better than writing the target in place, because the
                // window in which the file is incomplete is a rename rather than the whole write.
                Files.move(temporaryPath, logPath, StandardCopyOption.REPLACE_EXISTING);
            }
            temporaryPath = null;
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not write the failed build logs of submission " + submissionId + " to " + logPath, e);
        }
        finally {
            deleteTemporaryFile(temporaryPath);
        }

        return normalized;
    }

    /**
     * Removes a temporary file that never made it into place, so that a run of failed writes does not leave one behind for each attempt.
     */
    private static void deleteTemporaryFile(@Nullable Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException e) {
            log.warn("Could not remove the temporary build log file {}", path, e);
        }
    }

    /**
     * Reads the stored build logs of a failed build.
     *
     * @param submissionId the programming submission to read the logs of
     * @return the entries, or {@link Optional#empty()} if nothing is stored for this submission. Empty is not the same as an empty list: it is what tells the caller to look
     *         for the logs of a build that predates this store.
     */
    public Optional<List<BuildLogEntry>> getBuildLogs(long submissionId) {
        Path logPath = pathFor(submissionId);
        if (!Files.isRegularFile(logPath)) {
            return Optional.empty();
        }

        List<BuildLogEntry> entries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                parseLine(line, submissionId).ifPresent(entries::add);
            }
        }
        catch (IOException e) {
            log.error("Could not read the failed build logs of submission {} from {}", submissionId, logPath, e);
            return Optional.empty();
        }

        // The wire contract of participations/{id}/buildlogs keeps an id per entry for the out-of-repo IntelliJ plugin. A file has no row ids, so the position in the file
        // stands in for one: unique within the response and stable across reads, which is everything a list of log lines needs an id for.
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setId((long) (i + 1));
        }
        return Optional.of(entries);
    }

    /**
     * Removes the stored build logs of a submission, if there are any.
     *
     * @param submissionId the programming submission to delete the logs of
     * @throws UncheckedIOException if the file is there and cannot be removed. Swallowing that would delete a submission while the build logs of its student stay on disk
     *                                  until the retention period expires, which is the one outcome a deletion path must not produce quietly.
     */
    public void deleteBuildLogs(long submissionId) {
        Path logPath = pathFor(submissionId);
        try {
            Files.deleteIfExists(logPath);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not delete the failed build logs of submission " + submissionId + " at " + logPath, e);
        }
    }

    /**
     * Splits every entry whose log spans several lines into one entry per line, all keeping the timestamp of the entry they came from, and truncates each line to the length
     * the build output panel has always received.
     * <p>
     * A trailing line break produces no extra entry, because {@link String#split(String)} drops trailing empty pieces; a blank line between two lines does, so a message that
     * separates its parts with an empty line still reads that way.
     *
     * @param buildLogs the entries to normalize
     * @return the entries, none of which contains a line break
     */
    private static List<BuildLogEntry> splitIntoLines(List<BuildLogEntry> buildLogs) {
        List<BuildLogEntry> normalized = new ArrayList<>();
        for (BuildLogEntry entry : buildLogs) {
            if (entry.getLog() == null) {
                continue;
            }
            for (String line : entry.getLog().split("\\R")) {
                BuildLogEntry lineEntry = new BuildLogEntry(entry.getTime(), line);
                lineEntry.truncateLogToMaxLength();
                normalized.add(lineEntry);
            }
        }
        return normalized;
    }

    private Optional<BuildLogEntry> parseLine(String line, long submissionId) {
        int separator = line.indexOf(SEPARATOR);
        if (separator < 0) {
            log.warn("Skipping a malformed line in the failed build logs of submission {}", submissionId);
            return Optional.empty();
        }
        String timestamp = line.substring(0, separator);
        String message = line.substring(separator + 1);
        if (timestamp.isEmpty()) {
            return Optional.of(new BuildLogEntry(null, message));
        }
        try {
            return Optional.of(new BuildLogEntry(ZonedDateTime.parse(timestamp, TIMESTAMP_FORMAT), message));
        }
        catch (DateTimeParseException e) {
            log.warn("Skipping a line with an unparsable timestamp in the failed build logs of submission {}", submissionId);
            return Optional.empty();
        }
    }

    private Path pathFor(long submissionId) {
        return failedBuildLogsPath.resolve(String.valueOf(submissionId / SUBMISSIONS_PER_BUCKET)).resolve(submissionId + ".log");
    }

    /**
     * Deletes the stored logs of builds that failed longer ago than the retention period allows.
     */
    @Scheduled(cron = "${artemis.continuous-integration.build-log.failed-build-cleanup-schedule:0 30 3 * * ?}")
    public void deleteOldFailedBuildLogs() {
        if (!profileService.isSchedulingActive()) {
            return;
        }

        log.info("Deleting failed build logs older than {} days", retentionDays);
        if (!Files.isDirectory(failedBuildLogsPath)) {
            return;
        }

        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(retentionDays);
        int deleted = 0;
        try (DirectoryStream<Path> buckets = Files.newDirectoryStream(failedBuildLogsPath)) {
            for (Path bucket : buckets) {
                deleted += deleteExpiredLogsInBucket(bucket, cutoff);
            }
        }
        catch (IOException e) {
            log.error("Error occurred while deleting old failed build logs in {}", failedBuildLogsPath, e);
        }
        log.info("Deleted {} expired failed build log files", deleted);
    }

    private int deleteExpiredLogsInBucket(Path bucket, ZonedDateTime cutoff) {
        if (!Files.isDirectory(bucket)) {
            return 0;
        }

        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(bucket)) {
            for (Path file : files) {
                ZonedDateTime lastModified = ZonedDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), cutoff.getZone());
                if (Files.isRegularFile(file) && lastModified.isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
        }
        catch (IOException e) {
            log.error("Error occurred while deleting old failed build logs in {}", bucket, e);
            return deleted;
        }

        try (DirectoryStream<Path> remaining = Files.newDirectoryStream(bucket)) {
            if (!remaining.iterator().hasNext()) {
                Files.deleteIfExists(bucket);
            }
        }
        catch (IOException e) {
            log.error("Error occurred while removing the empty bucket directory {}", bucket, e);
        }
        return deleted;
    }
}
