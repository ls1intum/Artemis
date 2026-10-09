package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildLogEntryRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Unit tests for the job that drains what is left of {@code build_log_entry}.
 * <p>
 * Nothing writes to that table any more, so this job is the only thing that makes it shrink, and it is the reason the fallback read and the entity can eventually be deleted.
 * It runs against a backlog of millions of rows, so the behaviour that matters is that it works in batches, deletes each batch in one statement, and gives up its turn
 * rather than holding the database for as long as the whole backlog takes.
 */
@ExtendWith(MockitoExtension.class)
class BuildLogEntryRetentionTest {

    private static final int RETENTION_DAYS = 365;

    private static final int BATCH_SIZE = 5_000;

    private static final int MAX_BATCHES = 200;

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
        ReflectionTestUtils.setField(buildLogEntryService, "failedBuildRetentionDays", RETENTION_DAYS);
    }

    private static List<Long> ids(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }

    @Test
    void shouldDeleteExpiredRowsUntilNoneAreLeft() {
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(buildLogEntryRepository.findExpiredIds(any(), any())).thenReturn(ids(BATCH_SIZE), ids(120), List.of());

        buildLogEntryService.deleteExpiredBuildLogEntryRows();

        verify(buildLogEntryRepository).deleteAllByIdIn(ids(BATCH_SIZE));
        verify(buildLogEntryRepository).deleteAllByIdIn(ids(120));
        verify(buildLogEntryRepository, org.mockito.Mockito.times(3)).findExpiredIds(any(), any());
    }

    @Test
    void shouldCutOffAtTheRetentionPeriod() {
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(buildLogEntryRepository.findExpiredIds(any(), any())).thenReturn(List.of());
        // Bracketing the call rather than allowing a tolerance around a single reading: the cutoff the service computes
        // is taken from a clock reading that must fall between these two, so the assertion is exact and cannot become
        // flaky on a loaded runner the way a fixed one-minute window can.
        ZonedDateTime before = ZonedDateTime.now();

        buildLogEntryService.deleteExpiredBuildLogEntryRows();

        ZonedDateTime after = ZonedDateTime.now();
        ArgumentCaptor<ZonedDateTime> cutoff = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(buildLogEntryRepository).findExpiredIds(cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue()).isBetween(before.minusDays(RETENTION_DAYS), after.minusDays(RETENTION_DAYS));
    }

    /**
     * The backlog is larger than one run should attempt, so the job stops and leaves the rest for the next night rather than holding the database for as long as it takes.
     */
    @Test
    void shouldStopAtThePerRunLimitRatherThanDrainTheWholeBacklog() {
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(buildLogEntryRepository.findExpiredIds(any(), any())).thenReturn(ids(BATCH_SIZE));

        buildLogEntryService.deleteExpiredBuildLogEntryRows();

        verify(buildLogEntryRepository, org.mockito.Mockito.times(MAX_BATCHES)).findExpiredIds(any(), any());
        verify(buildLogEntryRepository, org.mockito.Mockito.times(MAX_BATCHES)).deleteAllByIdIn(any());
    }

    @Test
    void shouldNotTouchTheDatabaseWhenSchedulingIsInactive() {
        when(profileService.isSchedulingActive()).thenReturn(false);

        buildLogEntryService.deleteExpiredBuildLogEntryRows();

        verifyNoInteractions(buildLogEntryRepository);
        verify(buildLogEntryRepository, never()).deleteAllByIdIn(any());
    }
}
