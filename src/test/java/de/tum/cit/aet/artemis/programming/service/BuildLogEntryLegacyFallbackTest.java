package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.BuildLogEntryRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Unit tests for the rows a submission still has in {@code build_log_entry} after the build logs moved into the file store.
 * <p>
 * Those rows belong to the submission rather than to a result, so during the migration window a request for one specific result cannot simply be answered with them: a
 * submission that failed before the release and has since been rebuilt successfully still carries the old failure's rows, and answering the successful result with them
 * would show a build failure that never happened. The rows may therefore only answer for the one build they can have come from.
 */
@ExtendWith(MockitoExtension.class)
class BuildLogEntryLegacyFallbackTest {

    private static final long EXERCISE_ID = 1L;

    private static final long SUBMISSION_ID = 2L;

    private static final long FAILED_RESULT_ID = 10L;

    private static final long SUCCESSFUL_REBUILD_RESULT_ID = 11L;

    @Mock
    private BuildLogEntryRepository buildLogEntryRepository;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private BuildJobTestRepository buildJobRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private FailedBuildLogService failedBuildLogService;

    private BuildLogEntryService buildLogEntryService;

    @BeforeEach
    void setUp() {
        buildLogEntryService = new BuildLogEntryService(buildLogEntryRepository, programmingSubmissionRepository, profileService, buildJobRepository, programmingExerciseRepository,
                failedBuildLogService);
    }

    /**
     * A submission whose logs predate the file store: it has rows, but no file for any of its results.
     *
     * @param buildFailed whether the submission's current build is the failed one
     * @return the submission, with the legacy rows already stubbed on the repository
     */
    private ProgrammingSubmission submissionWithLegacyRows(boolean buildFailed) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setBuildFailed(buildFailed);
        when(programmingSubmissionRepository.findExerciseIdBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(EXERCISE_ID));
        return submission;
    }

    private void stubLegacyRows() {
        ProgrammingSubmission withRows = new ProgrammingSubmission();
        withRows.setId(SUBMISSION_ID);
        withRows.setBuildLogEntries(Set.of(new BuildLogEntry(ZonedDateTime.parse("2200-01-10T12:00:00Z"), "the earlier failure")));
        when(programmingSubmissionRepository.findWithEagerBuildLogEntriesById(SUBMISSION_ID)).thenReturn(Optional.of(withRows));
    }

    @Test
    void getBuildLogs_whenTheResultHasAFile_readsItInsteadOfTheRows() {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setId(SUBMISSION_ID);
        when(programmingSubmissionRepository.findExerciseIdBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(EXERCISE_ID));
        List<BuildLogEntry> stored = List.of(new BuildLogEntry(ZonedDateTime.parse("2200-01-10T12:00:00Z"), "this result's own logs"));
        when(failedBuildLogService.getBuildLogs(EXERCISE_ID, SUBMISSION_ID, FAILED_RESULT_ID)).thenReturn(Optional.of(stored));

        assertThat(buildLogEntryService.getBuildLogs(submission, FAILED_RESULT_ID)).as("the file of the requested result answers the request").isEqualTo(stored);
        verify(programmingSubmissionRepository, never()).findWithEagerBuildLogEntriesById(SUBMISSION_ID);
    }

    @Test
    void getBuildLogs_forTheFailedResultTheRowsCanOnlyBelongTo_fallsBackToThem() {
        ProgrammingSubmission submission = submissionWithLegacyRows(true);
        when(failedBuildLogService.getBuildLogs(EXERCISE_ID, SUBMISSION_ID, FAILED_RESULT_ID)).thenReturn(Optional.empty());
        when(programmingSubmissionRepository.findLatestResultIdBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(FAILED_RESULT_ID));
        stubLegacyRows();

        assertThat(buildLogEntryService.getBuildLogs(submission, FAILED_RESULT_ID)).as("the rows answer for the failed build they can only have come from")
                .extracting(BuildLogEntry::getLog).containsExactly("the earlier failure");
    }

    @Test
    void getBuildLogs_forASuccessfulRebuild_doesNotReturnTheEarlierFailuresRows() {
        // The submission failed before the release, so it still has rows, and has since been rebuilt successfully. Answering the successful result with those rows would
        // report a failure that this result never had.
        ProgrammingSubmission submission = submissionWithLegacyRows(false);
        when(failedBuildLogService.getBuildLogs(EXERCISE_ID, SUBMISSION_ID, SUCCESSFUL_REBUILD_RESULT_ID)).thenReturn(Optional.empty());

        assertThat(buildLogEntryService.getBuildLogs(submission, SUCCESSFUL_REBUILD_RESULT_ID)).as("a result whose build did not fail gets no logs").isEmpty();
        verify(programmingSubmissionRepository, never()).findWithEagerBuildLogEntriesById(SUBMISSION_ID);
    }

    @Test
    void getBuildLogs_forAResultThatIsNoLongerTheLatest_doesNotReturnTheRows() {
        // The rows carry no result of their own, so they cannot be attributed to an earlier result of the same submission.
        ProgrammingSubmission submission = submissionWithLegacyRows(true);
        when(failedBuildLogService.getBuildLogs(EXERCISE_ID, SUBMISSION_ID, FAILED_RESULT_ID)).thenReturn(Optional.empty());
        when(programmingSubmissionRepository.findLatestResultIdBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(SUCCESSFUL_REBUILD_RESULT_ID));

        assertThat(buildLogEntryService.getBuildLogs(submission, FAILED_RESULT_ID)).as("only the newest result may be answered with the rows").isEmpty();
        verify(programmingSubmissionRepository, never()).findWithEagerBuildLogEntriesById(SUBMISSION_ID);
    }

    @Test
    void getBuildLogs_whenTheSubmissionHasNoResultAtAll_returnsNothing() {
        ProgrammingSubmission submission = submissionWithLegacyRows(true);
        when(failedBuildLogService.getBuildLogs(EXERCISE_ID, SUBMISSION_ID, FAILED_RESULT_ID)).thenReturn(Optional.empty());
        when(programmingSubmissionRepository.findLatestResultIdBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.empty());

        assertThat(buildLogEntryService.getBuildLogs(submission, FAILED_RESULT_ID)).as("without a result there is nothing the rows could belong to").isEmpty();
    }
}
