package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Unit tests for the build logs of failed builds, which are kept on disk under {@code <failedBuildLogsPath>/<submissionId / 10000>/<submissionId>.log} rather than in the
 * database.
 * <p>
 * The file format carries structure that used to be carried by rows - one entry per line, {@code <timestamp>\t<log>} - so the properties that matter are that a read returns
 * exactly what a write put in, that nothing a build can print can forge an entry boundary, and that the retention job removes what has expired without taking anything else
 * with it.
 */
@ExtendWith(MockitoExtension.class)
class FailedBuildLogServiceTest {

    private static final long SUBMISSION_ID = 42L;

    private static final int RETENTION_DAYS = 365;

    private static final ZonedDateTime TIME = ZonedDateTime.parse("2026-09-16T10:15:30+02:00");

    @Mock
    private ProfileService profileService;

    @Mock
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @TempDir
    Path failedBuildLogsPath;

    private FailedBuildLogService failedBuildLogService;

    @BeforeEach
    void setUp() {
        failedBuildLogService = new FailedBuildLogService(profileService, programmingSubmissionRepository);
        ReflectionTestUtils.setField(failedBuildLogService, "failedBuildLogsPath", failedBuildLogsPath);
        ReflectionTestUtils.setField(failedBuildLogService, "retentionDays", RETENTION_DAYS);
    }

    @Test
    void shouldReturnEmptyWhenNothingIsStored() {
        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID)).isEmpty();
    }

    @Test
    void shouldRoundTripTimestampAndLog() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "compilation failed"), new BuildLogEntry(TIME, "1 error")));

        Optional<List<BuildLogEntry>> read = failedBuildLogService.getBuildLogs(SUBMISSION_ID);

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
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, multiLine)));

        List<BuildLogEntry> read = failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow();

        // the blank line between the first and the second part is preserved, because it is what the message uses to separate them
        assertThat(read).extracting(BuildLogEntry::getLog).containsExactly("error: externally-managed-environment", "", "× This environment is externally managed",
                "╰─> To install packages, try apt install");
        assertThat(read).allSatisfy(entry -> assertThat(entry.getTime()).isEqualTo(TIME));
    }

    @Test
    void shouldNotProduceAnExtraEntryForATrailingLineBreak() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "a single line\n")));

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("a single line");
    }

    @Test
    void shouldKeepATabInsideALogValue() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "Tests run: 3\tFailures: 1")));

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("Tests run: 3\tFailures: 1");
    }

    /**
     * The wire contract of {@code participations/{id}/buildlogs} keeps an id per entry for the out-of-repo IntelliJ plugin, and a file has no row ids to give it.
     */
    @Test
    void shouldNumberEntriesFromOne() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "first"), new BuildLogEntry(TIME, "second"), new BuildLogEntry(TIME, "third")));

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow()).extracting(BuildLogEntry::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldReplacePreviousLogsOfTheSameSubmission() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "from the first build")));
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "from the second build")));

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("from the second build");
    }

    @Test
    void shouldTruncateALineToTheLengthTheBuildOutputPanelHasAlwaysReceived() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "x".repeat(300))));

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow().getFirst().getLog()).hasSize(255);
    }

    @Test
    void shouldKeepSubmissionsInSeparateFiles() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "mine")));
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID + 1, List.of(new BuildLogEntry(TIME, "someone else's")));

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("mine");
        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID + 1).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("someone else's");
    }

    @Test
    void shouldBucketFilesBySubmissionIdSoThatADirectoryStaysSmall() {
        failedBuildLogService.saveBuildLogs(5L, List.of(new BuildLogEntry(TIME, "early")));
        failedBuildLogService.saveBuildLogs(20_001L, List.of(new BuildLogEntry(TIME, "later")));

        assertThat(failedBuildLogsPath.resolve("0").resolve("5.log")).exists();
        assertThat(failedBuildLogsPath.resolve("2").resolve("20001.log")).exists();
    }

    @Test
    void shouldDeleteTheLogsOfASubmission() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "to be deleted")));

        failedBuildLogService.deleteBuildLogs(SUBMISSION_ID);

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID)).isEmpty();
    }

    @Test
    void shouldNotFailWhenDeletingLogsThatWereNeverStored() {
        failedBuildLogService.deleteBuildLogs(SUBMISSION_ID);

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID)).isEmpty();
    }

    @Test
    void shouldDeleteExpiredLogsAndKeepTheRest() throws IOException {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "expired")));
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID + 1, List.of(new BuildLogEntry(TIME, "still within retention")));
        ageFile(failedBuildLogsPath.resolve("0").resolve(SUBMISSION_ID + ".log"), RETENTION_DAYS + 1);
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(programmingSubmissionRepository.findExistingIds(anySet())).thenAnswer(invocation -> invocation.getArgument(0));

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID)).isEmpty();
        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID + 1)).isPresent();
    }

    /**
     * The store is keyed by submission id but has no foreign key to hold it to one, so a build result that is still being processed can write the file back after the
     * submission was deleted. Nothing else would then remove the build output of a deleted submission before the retention period expires.
     */
    @Test
    void shouldDeleteTheLogsOfASubmissionThatNoLongerExists() {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "belongs to a deleted submission")));
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID + 1, List.of(new BuildLogEntry(TIME, "belongs to a live submission")));
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(programmingSubmissionRepository.findExistingIds(anySet())).thenReturn(Set.of(SUBMISSION_ID + 1));

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID)).isEmpty();
        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID + 1)).isPresent();
    }

    /**
     * A temporary file left behind by a write that was interrupted names no submission, so the sweep must not read one out of it and must not delete it as an orphan either:
     * only the expiry rule applies to it.
     */
    @Test
    void shouldLeaveAFileThatNamesNoSubmissionToTheExpiryRule() throws IOException {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "a real log")));
        Path stray = failedBuildLogsPath.resolve("0").resolve("42-17681234.tmp");
        Files.writeString(stray, "half a write");
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(programmingSubmissionRepository.findExistingIds(anySet())).thenAnswer(invocation -> invocation.getArgument(0));

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(stray).exists();
        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID)).isPresent();
    }

    @Test
    void shouldRemoveABucketDirectoryOnceItIsEmpty() throws IOException {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "expired")));
        ageFile(failedBuildLogsPath.resolve("0").resolve(SUBMISSION_ID + ".log"), RETENTION_DAYS + 1);
        when(profileService.isSchedulingActive()).thenReturn(true);

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(failedBuildLogsPath.resolve("0")).doesNotExist();
    }

    @Test
    void shouldNotDeleteAnythingWhenSchedulingIsInactive() throws IOException {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "expired")));
        ageFile(failedBuildLogsPath.resolve("0").resolve(SUBMISSION_ID + ".log"), RETENTION_DAYS + 1);
        when(profileService.isSchedulingActive()).thenReturn(false);

        failedBuildLogService.deleteOldFailedBuildLogs();

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID)).isPresent();
    }

    @Test
    void shouldSkipAMalformedLineRatherThanFailTheWholeRead() throws IOException {
        Path logPath = failedBuildLogsPath.resolve("0").resolve(SUBMISSION_ID + ".log");
        Files.createDirectories(logPath.getParent());
        Files.writeString(logPath, "a line without a separator\n" + TIME + "\tthe good one\n");

        assertThat(failedBuildLogService.getBuildLogs(SUBMISSION_ID).orElseThrow()).extracting(BuildLogEntry::getLog).containsExactly("the good one");
    }

    private static void ageFile(Path file, int days) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(days, ChronoUnit.DAYS)));
    }

    /**
     * A write that fails has to say so. The caller deletes the rows this file replaces, and doing that after a silent failure leaves the submission with neither.
     */
    @Test
    void shouldReportAFailedWriteRatherThanReturnNormally() throws IOException {
        // a regular file where the bucket directory belongs, so that creating the directory cannot succeed
        Files.createFile(failedBuildLogsPath.resolve("0"));

        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "lost"))));
    }

    @Test
    void shouldNotLeaveATemporaryFileBehindWhenTheWriteSucceeds() throws IOException {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "kept")));

        try (var files = Files.list(failedBuildLogsPath.resolve("0"))) {
            assertThat(files.map(Path::toString)).containsExactly(failedBuildLogsPath.resolve("0").resolve(SUBMISSION_ID + ".log").toString());
        }
    }

    /**
     * Deleting a submission whose build logs stay on disk is the one outcome this path must not produce quietly, so a failure has to reach the caller.
     */
    @Test
    void shouldReportAFailedDeleteRatherThanReturnNormally() throws IOException {
        failedBuildLogService.saveBuildLogs(SUBMISSION_ID, List.of(new BuildLogEntry(TIME, "to be deleted")));
        Path bucket = failedBuildLogsPath.resolve("0");
        // a directory in place of the file: deleteIfExists refuses it rather than reporting that nothing was there
        Files.delete(bucket.resolve(SUBMISSION_ID + ".log"));
        Files.createDirectory(bucket.resolve(SUBMISSION_ID + ".log"));
        Files.createFile(bucket.resolve(SUBMISSION_ID + ".log").resolve("occupied"));

        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> failedBuildLogService.deleteBuildLogs(SUBMISSION_ID));
    }
}
