package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/**
 * Unit tests for the build logs of failed builds, which are kept on disk under {@code <failedBuildLogsPath>/<exerciseId>/<submissionId>/<resultId>.log} rather than in the
 * database.
 * <p>
 * The file format carries structure that used to be carried by rows - one entry per line, {@code <timestamp>\t<log>} - so the properties that matter are that a read returns
 * exactly what a write put in, that nothing a build can print can forge an entry boundary, and that the retention job removes what has expired without taking anything else
 * with it.
 */
@ExtendWith(MockitoExtension.class)
class FailedBuildLogServiceTest {

    private static final long EXERCISE_ID = 7L;

    private static final long SUBMISSION_ID = 42L;

    private static final long RESULT_ID = 80L;

    private static final int RETENTION_DAYS = 365;

    private static final ZonedDateTime TIME = ZonedDateTime.parse("2026-09-16T10:15:30+02:00");

    @Mock
    private ProfileService profileService;

    @TempDir
    Path failedBuildLogsPath;

    private FailedBuildLogService failedBuildLogService;

    @BeforeEach
    void setUp() {
        failedBuildLogService = new FailedBuildLogService(profileService);
        ReflectionTestUtils.setField(failedBuildLogService, "failedBuildLogsPath", failedBuildLogsPath);
        ReflectionTestUtils.setField(failedBuildLogService, "retentionDays", RETENTION_DAYS);
    }

    private List<BuildLogEntry> save(long resultId, ZonedDateTime retentionTime, BuildLogEntry... entries) {
        return failedBuildLogService.saveBuildLogs(EXERCISE_ID, SUBMISSION_ID, resultId, retentionTime, List.of(entries));
    }

    private List<BuildLogEntry> save(long resultId, BuildLogEntry... entries) {
        return save(resultId, TIME, entries);
    }

    private Optional<List<BuildLogEntry>> get(long resultId) {
        return failedBuildLogService.getBuildLogs(EXERCISE_ID, SUBMISSION_ID, resultId);
    }

    private Path submissionPath() {
        return failedBuildLogsPath.resolve(String.valueOf(EXERCISE_ID)).resolve(String.valueOf(SUBMISSION_ID));
    }

    @Test
    void shouldReturnEmptyWhenNothingIsStored() {
        assertThat(get(RESULT_ID)).isEmpty();
    }

    @Test
    void shouldRoundTripTimestampAndLog() {
        save(RESULT_ID, new BuildLogEntry(TIME, "compilation failed"), new BuildLogEntry(TIME, "1 error"));

        Optional<List<BuildLogEntry>> read = get(RESULT_ID);

        assertThat(read).isPresent();
        assertThat(read.get()).extracting(BuildLogEntry::getLog).containsExactly("compilation failed", "1 error");
        assertThat(read.get()).allSatisfy(entry -> assertThat(entry.getTime()).isEqualTo(TIME));
    }

    /**
     * The reason the store exists in this shape: an entry whose log carries a line break would otherwise forge an entry boundary when the file is read back.
     */
    @Test
    void shouldSplitAMultiLineEntryIntoOneEntryPerLineKeepingTheTimestamp() {
        String multiLine = "error: externally-managed-environment\n\n× This environment is externally managed\n╰─> To install packages, try apt install";
        save(RESULT_ID, new BuildLogEntry(TIME, multiLine));

        List<BuildLogEntry> read = get(RESULT_ID).orElseThrow();

        // the blank line between the first and the second part is preserved, because it is what the message uses to separate them
        assertThat(read).extracting(BuildLogEntry::getLog).containsExactly("error: externally-managed-environment", "", "× This environment is externally managed",
                "╰─> To install packages, try apt install");
        assertThat(read).allSatisfy(entry -> assertThat(entry.getTime()).isEqualTo(TIME));
    }

    @Test
    void shouldNotProduceAnExtraEntryForATrailingLineBreak() {
        save(RESULT_ID, new BuildLogEntry(TIME, "a single line\n"));

        assertThat(get(RESULT_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("a single line");
    }

    @Test
    void shouldKeepATabInsideALogValue() {
        save(RESULT_ID, new BuildLogEntry(TIME, "Tests run: 3\tFailures: 1"));

        assertThat(get(RESULT_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("Tests run: 3\tFailures: 1");
    }

    /**
     * The wire contract of {@code participations/{id}/buildlogs} keeps an id per entry for the out-of-repo IntelliJ plugin, and a file has no row ids to give it.
     */
    @Test
    void shouldNumberEntriesFromOne() {
        save(RESULT_ID, new BuildLogEntry(TIME, "first"), new BuildLogEntry(TIME, "second"), new BuildLogEntry(TIME, "third"));

        assertThat(get(RESULT_ID).orElseThrow()).extracting(BuildLogEntry::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldKeepLogsOfMultipleResultsOfTheSameSubmission() {
        save(RESULT_ID, new BuildLogEntry(TIME, "from the first build"));
        save(RESULT_ID + 1, new BuildLogEntry(TIME.plusMinutes(1), "from the second build"));

        assertThat(get(RESULT_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("from the first build");
        assertThat(get(RESULT_ID + 1).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("from the second build");
        assertThat(failedBuildLogService.getLatestBuildLogs(EXERCISE_ID, SUBMISSION_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("from the second build");
    }

    @Test
    void shouldTruncateALineToTheLengthTheBuildOutputPanelHasAlwaysReceived() {
        save(RESULT_ID, new BuildLogEntry(TIME, "x".repeat(300)));

        assertThat(get(RESULT_ID).orElseThrow().getFirst().getLog()).hasSize(255);
    }

    @Test
    void shouldKeepSubmissionsInSeparateFiles() {
        save(RESULT_ID, new BuildLogEntry(TIME, "mine"));
        failedBuildLogService.saveBuildLogs(EXERCISE_ID, SUBMISSION_ID + 1, RESULT_ID + 1, TIME, List.of(new BuildLogEntry(TIME, "someone else's")));

        assertThat(get(RESULT_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("mine");
        assertThat(failedBuildLogService.getBuildLogs(EXERCISE_ID, SUBMISSION_ID + 1, RESULT_ID + 1).orElseThrow()).extracting(BuildLogEntry::getLog)
                .containsExactly("someone else's");
    }

    @Test
    void shouldGroupResultsByExerciseAndSubmission() {
        save(RESULT_ID, new BuildLogEntry(TIME, "first result"));
        failedBuildLogService.saveBuildLogs(EXERCISE_ID + 1, SUBMISSION_ID + 1, RESULT_ID + 1, TIME, List.of(new BuildLogEntry(TIME, "another exercise")));

        assertThat(failedBuildLogsPath.resolve("7").resolve("42").resolve("80.log")).isRegularFile();
        assertThat(failedBuildLogsPath.resolve("8").resolve("43").resolve("81.log")).isRegularFile();
    }

    @Test
    void shouldDeleteAllResultLogsOfASubmission() {
        save(RESULT_ID, new BuildLogEntry(TIME, "first"));
        save(RESULT_ID + 1, new BuildLogEntry(TIME, "second"));

        failedBuildLogService.deleteBuildLogs(EXERCISE_ID, SUBMISSION_ID);

        assertThat(submissionPath()).doesNotExist();
        assertThat(get(RESULT_ID)).isEmpty();
        assertThat(get(RESULT_ID + 1)).isEmpty();
    }

    @Test
    void shouldNotFailWhenDeletingLogsThatWereNeverStored() {
        failedBuildLogService.deleteBuildLogs(EXERCISE_ID, SUBMISSION_ID);

        assertThat(get(RESULT_ID)).isEmpty();
    }

    @Test
    void shouldDeleteLogsByTheirResultTimestampAndKeepTheRest() {
        ZonedDateTime now = ZonedDateTime.now();
        save(RESULT_ID, now.minusDays(RETENTION_DAYS + 1L), new BuildLogEntry(TIME, "expired"));
        save(RESULT_ID + 1, now.minusDays(RETENTION_DAYS - 1L), new BuildLogEntry(TIME, "still within retention"));
        when(profileService.isSchedulingActive()).thenReturn(true);

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(get(RESULT_ID)).isEmpty();
        assertThat(get(RESULT_ID + 1)).isPresent();
    }

    /**
     * A temporary file left behind by an interrupted write follows the same timestamp-only retention rule as published logs.
     */
    @Test
    void shouldKeepAnUnexpiredTemporaryFile() throws IOException {
        save(RESULT_ID, new BuildLogEntry(TIME, "a real log"));
        Path stray = submissionPath().resolve("42-17681234.tmp");
        Files.writeString(stray, "half a write");
        when(profileService.isSchedulingActive()).thenReturn(true);

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(stray).exists();
        assertThat(get(RESULT_ID)).isPresent();
    }

    @Test
    void shouldKeepTheDirectoryHierarchyAfterDeletingItsLastLog() {
        save(RESULT_ID, ZonedDateTime.now().minusDays(RETENTION_DAYS + 1L), new BuildLogEntry(TIME, "expired"));
        when(profileService.isSchedulingActive()).thenReturn(true);

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(submissionPath()).isDirectory().isEmptyDirectory();
    }

    @Test
    void shouldNotDeleteAnythingWhenSchedulingIsInactive() {
        save(RESULT_ID, ZonedDateTime.now().minusDays(RETENTION_DAYS + 1L), new BuildLogEntry(TIME, "expired"));
        when(profileService.isSchedulingActive()).thenReturn(false);

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(get(RESULT_ID)).isPresent();
    }

    @Test
    void shouldSkipAMalformedLineRatherThanFailTheWholeRead() throws IOException {
        Files.createDirectories(submissionPath());
        Path logPath = submissionPath().resolve(RESULT_ID + ".log");
        Files.writeString(logPath, "a line without a separator\n" + TIME + "\tthe good one\n");

        assertThat(get(RESULT_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("the good one");
    }

    /**
     * A write that fails has to say so. The caller deletes the rows this file replaces, and doing that after a silent failure leaves the submission with neither.
     */
    @Test
    void shouldReportAFailedWriteRatherThanReturnNormally() throws IOException {
        // a regular file where the store directory belongs, so that creating the directory cannot succeed
        Path blockedStore = failedBuildLogsPath.resolve("blocked");
        Files.createFile(blockedStore);
        ReflectionTestUtils.setField(failedBuildLogService, "failedBuildLogsPath", blockedStore);

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> failedBuildLogService.saveBuildLogs(EXERCISE_ID, SUBMISSION_ID, RESULT_ID, TIME, List.of(new BuildLogEntry(TIME, "lost"))));
    }

    @Test
    void shouldNotLeaveATemporaryFileBehindWhenTheWriteSucceeds() throws IOException {
        save(RESULT_ID, new BuildLogEntry(TIME, "kept"));

        try (var files = Files.list(submissionPath())) {
            assertThat(files.map(Path::toString)).containsExactly(submissionPath().resolve(RESULT_ID + ".log").toString());
        }
    }
}
