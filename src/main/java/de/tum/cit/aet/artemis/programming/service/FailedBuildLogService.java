package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
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
 * Stores the build logs of failed builds on disk, one file per result.
 * <p>
 * This is deliberately not the store behind {@code artemis.build-logs-path}. That one keeps the complete log of every build job for a short period, is keyed by build job id,
 * and is only ever streamed to the browser as a download. This one keeps the logs of failed builds for a year, is keyed by exercise, submission and result, and is parsed back
 * into entries to serve the build output panel, Iris and Hyperion. Sharing one store would force one retention period onto both.
 * <p>
 * <b>Layout.</b> {@code <root>/<exerciseId>/<submissionId>/<resultId>.log}. Keeping every exercise in its own directory bounds the number of entries at each level, while the
 * result id preserves the logs of multiple failed builds for one submission.
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

    private static final char SEPARATOR = '\t';

    private static final String LOG_SUFFIX = ".log";

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
     * Writes the build logs of a failed result. A different result of the same submission is stored in its own file.
     * <p>
     * Multi-line entries are split into one entry per line, all keeping the timestamp of the entry they came from, so that no stored value contains a line break.
     *
     * @param exerciseId    the programming exercise the result belongs to
     * @param submissionId  the programming submission the result belongs to
     * @param resultId      the result the logs belong to
     * @param retentionTime the result or submission timestamp used for retention
     * @param buildLogs     the entries to store
     * @return the entries as they were stored, which is what a subsequent read returns
     * @throws UncheckedIOException if the logs could not be written. The caller has to know, because that is what decides whether the rows this file replaces may go.
     */
    public List<BuildLogEntry> saveBuildLogs(long exerciseId, long submissionId, long resultId, ZonedDateTime retentionTime, List<BuildLogEntry> buildLogs) {
        List<BuildLogEntry> normalized = splitIntoLines(buildLogs);
        Path logPath = pathFor(exerciseId, submissionId, resultId);

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
            // File age is the retention contract. Set it on the temporary file so that the published file is never briefly stamped with the server's wall-clock time.
            Files.setLastModifiedTime(temporaryPath, FileTime.from(retentionTime.toInstant()));
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
            throw new UncheckedIOException("Could not write the failed build logs of result " + resultId + " to " + logPath, e);
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
     * Reads the stored build logs of a failed result.
     *
     * @param exerciseId   the programming exercise the result belongs to
     * @param submissionId the programming submission the result belongs to
     * @param resultId     the result to read the logs of
     * @return the entries, or {@link Optional#empty()} if nothing is stored for this result. Empty is not the same as an empty list: it is what tells the caller to look
     *         for the logs of a build that predates this store.
     */
    public Optional<List<BuildLogEntry>> getBuildLogs(long exerciseId, long submissionId, long resultId) {
        return readBuildLogs(pathFor(exerciseId, submissionId, resultId), submissionId, resultId);
    }

    /**
     * Reads the most recently stored failed-result logs of a submission.
     *
     * @param exerciseId   the programming exercise the submission belongs to
     * @param submissionId the programming submission to read the latest logs of
     * @return the latest result's entries, or {@link Optional#empty()} if none are stored
     */
    public Optional<List<BuildLogEntry>> getLatestBuildLogs(long exerciseId, long submissionId) {
        Path submissionPath = submissionPathFor(exerciseId, submissionId);
        long latestResultId = Long.MIN_VALUE;
        Path latestLogPath = null;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(submissionPath, "*" + LOG_SUFFIX)) {
            for (Path file : files) {
                Optional<Long> resultId = resultIdOf(file);
                if (resultId.isPresent() && resultId.get() > latestResultId) {
                    latestResultId = resultId.get();
                    latestLogPath = file;
                }
            }
        }
        catch (NoSuchFileException e) {
            return Optional.empty();
        }
        catch (IOException e) {
            log.error("Could not find the latest failed build logs of submission {} in {}", submissionId, submissionPath, e);
            return Optional.empty();
        }

        return latestLogPath == null ? Optional.empty() : readBuildLogs(latestLogPath, submissionId, latestResultId);
    }

    private Optional<List<BuildLogEntry>> readBuildLogs(Path logPath, long submissionId, long resultId) {
        if (!Files.isRegularFile(logPath)) {
            return Optional.empty();
        }

        List<BuildLogEntry> entries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                parseLine(line, submissionId, resultId).ifPresent(entries::add);
            }
        }
        catch (IOException e) {
            log.error("Could not read the failed build logs of result {} of submission {} from {}", resultId, submissionId, logPath, e);
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
     * Removes every stored failed-result log of a submission, if there are any.
     *
     * @param exerciseId   the programming exercise the submission belongs to
     * @param submissionId the programming submission to delete the logs of
     * @throws UncheckedIOException if the directory is there and cannot be removed. Swallowing that would delete a submission while the build logs of its student stay on disk
     *                                  until the retention period expires, which is the one outcome a deletion path must not produce quietly.
     */
    public void deleteBuildLogs(long exerciseId, long submissionId) {
        Path submissionPath = submissionPathFor(exerciseId, submissionId);
        try {
            deleteRecursively(submissionPath);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not delete the failed build logs of submission " + submissionId + " at " + submissionPath, e);
        }
    }

    /**
     * Removes the stored log of one result, leaving the other results of the same submission alone.
     * <p>
     * A result that is updated in place keeps its id, so the file a failed build wrote for it would otherwise still be there once a later build of the same result succeeded,
     * and every read of that result would answer with a failure it no longer had.
     *
     * @param exerciseId   the programming exercise the result belongs to
     * @param submissionId the programming submission the result belongs to
     * @param resultId     the result to delete the logs of
     */
    public void deleteBuildLogs(long exerciseId, long submissionId, long resultId) {
        Path logPath = pathFor(exerciseId, submissionId, resultId);
        try {
            Files.deleteIfExists(logPath);
        }
        catch (IOException e) {
            // Unlike deleting a submission, this is a correction of what a read would show rather than a deletion someone asked for, and failing the build result over it
            // would turn a stale log into a lost one.
            log.error("Could not delete the failed build logs of result {} at {}", resultId, logPath, e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(path);
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
            for (Path child : children) {
                deleteRecursively(child);
            }
        }
        Files.delete(path);
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

    private Optional<BuildLogEntry> parseLine(String line, long submissionId, long resultId) {
        int separator = line.indexOf(SEPARATOR);
        if (separator < 0) {
            log.warn("Skipping a malformed line in the failed build logs of result {} of submission {}", resultId, submissionId);
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
            log.warn("Skipping a line with an unparsable timestamp in the failed build logs of result {} of submission {}", resultId, submissionId);
            return Optional.empty();
        }
    }

    private Path pathFor(long exerciseId, long submissionId, long resultId) {
        return submissionPathFor(exerciseId, submissionId).resolve(resultId + LOG_SUFFIX);
    }

    private Path submissionPathFor(long exerciseId, long submissionId) {
        return failedBuildLogsPath.resolve(String.valueOf(exerciseId)).resolve(String.valueOf(submissionId));
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
        int deletedExpiredLogs = deleteExpiredLogs(failedBuildLogsPath, cutoff);
        int deletedOrphanedSubmissions = deleteLogsOfDeletedSubmissions();
        log.info("Deleted {} expired failed build log files and the log directories of {} deleted submissions", deletedExpiredLogs, deletedOrphanedSubmissions);
    }

    private int deleteExpiredLogs(Path path, ZonedDateTime cutoff) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return deleteIfExpired(path, cutoff) ? 1 : 0;
        }

        int deleted = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
            for (Path child : children) {
                deleted += deleteExpiredLogs(child, cutoff);
            }
        }
        catch (IOException e) {
            log.error("Error occurred while deleting old failed build logs in {}", path, e);
        }
        return deleted;
    }

    /**
     * Deletes log directories whose submissions no longer exist.
     * <p>
     * Normal retention remains based only on the result timestamp stored as the file's last-modified time. This separate sweep handles a deletion race: a result that finishes
     * while its submission is being deleted can publish its log after the deletion path removed the submission directory. Without the sweep, that orphan would remain until
     * its regular retention period expires.
     *
     * @return how many orphaned submission directories were deleted
     */
    private int deleteLogsOfDeletedSubmissions() {
        Map<Long, Path> submissionDirectories = new HashMap<>(SUBMISSION_LOOKUP_BATCH_SIZE);
        int deleted = 0;
        try (DirectoryStream<Path> exerciseDirectories = Files.newDirectoryStream(failedBuildLogsPath)) {
            for (Path exerciseDirectory : exerciseDirectories) {
                if (!Files.isDirectory(exerciseDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                deleted += collectAndDeleteOrphanedSubmissionDirectories(exerciseDirectory, submissionDirectories);
            }
        }
        catch (IOException e) {
            log.error("Error occurred while finding orphaned failed build logs in {}", failedBuildLogsPath, e);
        }
        return deleted + deleteOrphanedSubmissionDirectories(submissionDirectories);
    }

    private int collectAndDeleteOrphanedSubmissionDirectories(Path exerciseDirectory, Map<Long, Path> submissionDirectories) {
        int deleted = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(exerciseDirectory)) {
            for (Path submissionDirectory : children) {
                if (!Files.isDirectory(submissionDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Optional<Long> submissionId = directoryIdOf(submissionDirectory);
                if (submissionId.isEmpty()) {
                    continue;
                }
                submissionDirectories.put(submissionId.get(), submissionDirectory);
                if (submissionDirectories.size() == SUBMISSION_LOOKUP_BATCH_SIZE) {
                    deleted += deleteOrphanedSubmissionDirectories(submissionDirectories);
                    submissionDirectories.clear();
                }
            }
        }
        catch (IOException e) {
            log.error("Error occurred while finding orphaned failed build logs in {}", exerciseDirectory, e);
        }
        return deleted;
    }

    private int deleteOrphanedSubmissionDirectories(Map<Long, Path> submissionDirectories) {
        if (submissionDirectories.isEmpty()) {
            return 0;
        }

        Set<Long> existingSubmissionIds = programmingSubmissionRepository.findExistingIds(Set.copyOf(submissionDirectories.keySet()));
        int deleted = 0;
        for (Map.Entry<Long, Path> submissionDirectory : submissionDirectories.entrySet()) {
            if (existingSubmissionIds.contains(submissionDirectory.getKey())) {
                continue;
            }
            try {
                deleteRecursively(submissionDirectory.getValue());
                deleted++;
                log.debug("Deleted the failed build logs of submission {}, which no longer exists", submissionDirectory.getKey());
            }
            catch (IOException e) {
                log.warn("Could not delete the failed build log directory {} of deleted submission {}", submissionDirectory.getValue(), submissionDirectory.getKey(), e);
            }
        }
        return deleted;
    }

    /**
     * Deletes one file if it is a regular file whose build failed before the cutoff.
     * <p>
     * A file that disappears while the directory is walked is not an error: the submission it belongs to can be deleted at any time, and letting that abort the walk would leave
     * the rest of the files unexamined until the next run.
     *
     * @return whether the file was deleted
     */
    private boolean deleteIfExpired(Path file, ZonedDateTime cutoff) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
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
     * The result a log file belongs to, or empty for anything this store did not publish, such as a temporary file left behind by an interrupted write.
     */
    private static Optional<Long> resultIdOf(Path file) {
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

    private static Optional<Long> directoryIdOf(Path directory) {
        try {
            return Optional.of(Long.parseLong(directory.getFileName().toString()));
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

}
