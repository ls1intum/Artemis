package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Stores the build logs of failed builds on disk, one file per programming submission.
 * <p>
 * This is deliberately not the store behind {@code artemis.build-logs-path}. That one keeps the complete log of every build job for a short period, is keyed by build job id,
 * and is only ever streamed to the browser as a download. This one keeps the logs of failed builds for a year, is keyed by submission id because that is all its readers have,
 * and is parsed back into entries to serve the build output panel, Iris and Hyperion. Sharing one store would force one retention period onto both.
 * <p>
 * <b>Layout.</b> {@code <root>/<submissionId / 10000>/<submissionId>.log}. The bucket bounds the size of a directory while still finding a file from nothing but a submission
 * id, which is all the readers have.
 * <p>
 * <b>Format.</b> One entry per line, {@code <ISO-8601 timestamp>\t<log>}, which is unambiguous only because {@link #splitIntoLines} guarantees no stored value contains a line
 * break: an entry whose log spans several lines is written as several entries sharing its timestamp. Nothing observes the difference, because the build output panel groups
 * consecutive entries and prints a timestamp only when it changes. A tab inside a log value is harmless because only the first one separates. {@code BuildLogParseUtils}
 * applies the same rule to Jenkins logs, for the same reason.
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

    private static final String LOG_SUFFIX = ".log";

    /**
     * How many submission ids the cleanup asks about at once. A bucket holds up to {@link #SUBMISSIONS_PER_BUCKET} files and the query names every id it is given.
     */
    private static final int SUBMISSION_LOOKUP_BATCH_SIZE = 1_000;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ProfileService profileService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Value("${artemis.failed-build-logs-path:./failed-build-logs}")
    private Path failedBuildLogsPath;

    @Value("${artemis.continuous-integration.build-log.failed-build-retention-days:365}")
    private int retentionDays;

    public FailedBuildLogService(ProfileService profileService, ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.profileService = profileService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
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
        return failedBuildLogsPath.resolve(String.valueOf(submissionId / SUBMISSIONS_PER_BUCKET)).resolve(submissionId + LOG_SUFFIX);
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
        Map<Long, Path> survivingFilesBySubmissionId = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(bucket)) {
            for (Path file : files) {
                if (deleteIfExpired(file, cutoff)) {
                    deleted++;
                    continue;
                }
                submissionIdOf(file).ifPresent(submissionId -> survivingFilesBySubmissionId.put(submissionId, file));
            }
        }
        catch (IOException e) {
            log.error("Error occurred while deleting old failed build logs in {}", bucket, e);
            return deleted;
        }

        deleted += deleteLogsOfDeletedSubmissions(survivingFilesBySubmissionId);
        deleteBucketIfEmpty(bucket);
        return deleted;
    }

    /**
     * Deletes one file if it is a regular file whose build failed before the cutoff.
     * <p>
     * A file that disappears while the bucket is walked is not an error: the submission it belongs to can be deleted at any time, and letting that abort the walk would leave
     * the rest of a bucket, up to {@link #SUBMISSIONS_PER_BUCKET} files, unexamined until the next run.
     *
     * @return whether the file was deleted
     */
    private boolean deleteIfExpired(Path file, ZonedDateTime cutoff) {
        try {
            if (!Files.isRegularFile(file)) {
                return false;
            }
            ZonedDateTime lastModified = ZonedDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), cutoff.getZone());
            return lastModified.isBefore(cutoff) && Files.deleteIfExists(file);
        }
        catch (NoSuchFileException e) {
            return false;
        }
        catch (IOException e) {
            log.warn("Could not delete the expired failed build log file {}", file, e);
            return false;
        }
    }

    /**
     * Deletes the log files of submissions that no longer exist.
     * <p>
     * Nothing deletes such a file at the moment its submission goes: the store is keyed by submission id but has no foreign key, so a build result that is still being
     * processed can write the file back after {@link #deleteBuildLogs} removed it. Without this sweep that file would hold the build output of a deleted submission for the
     * rest of the retention period. It runs on the scheduling node only, once per bucket, against ids alone.
     *
     * @param logFilesBySubmissionId the unexpired files of this bucket, by the submission they belong to
     * @return how many files were deleted
     */
    private int deleteLogsOfDeletedSubmissions(Map<Long, Path> logFilesBySubmissionId) {
        int deleted = 0;
        List<Long> submissionIds = List.copyOf(logFilesBySubmissionId.keySet());
        for (int start = 0; start < submissionIds.size(); start += SUBMISSION_LOOKUP_BATCH_SIZE) {
            Set<Long> batch = Set.copyOf(submissionIds.subList(start, Math.min(start + SUBMISSION_LOOKUP_BATCH_SIZE, submissionIds.size())));
            Set<Long> existing = programmingSubmissionRepository.findExistingIds(batch);
            for (Long submissionId : batch) {
                if (existing.contains(submissionId)) {
                    continue;
                }
                Path file = logFilesBySubmissionId.get(submissionId);
                try {
                    if (Files.deleteIfExists(file)) {
                        log.debug("Deleted the failed build logs of submission {}, which no longer exists", submissionId);
                        deleted++;
                    }
                }
                catch (IOException e) {
                    log.warn("Could not delete the failed build log file {} of the deleted submission {}", file, submissionId, e);
                }
            }
        }
        return deleted;
    }

    /**
     * The submission a log file belongs to, or empty for anything this store did not write, such as a temporary file left behind by a write that was interrupted.
     */
    private static Optional<Long> submissionIdOf(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(LOG_SUFFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(name.substring(0, name.length() - LOG_SUFFIX.length())));
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void deleteBucketIfEmpty(Path bucket) {
        try (DirectoryStream<Path> remaining = Files.newDirectoryStream(bucket)) {
            if (!remaining.iterator().hasNext()) {
                Files.deleteIfExists(bucket);
            }
        }
        catch (IOException e) {
            log.error("Error occurred while removing the empty bucket directory {}", bucket, e);
        }
    }
}
