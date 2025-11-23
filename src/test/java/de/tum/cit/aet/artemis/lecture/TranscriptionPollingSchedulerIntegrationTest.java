package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.nebula.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.nebula.service.NebulaTranscriptionPollingScheduler;

/**
 * Unit test for {@link NebulaTranscriptionPollingScheduler}.
 *
 * This test validates the scheduler's ability to poll pending transcriptions and process them correctly.
 * Uses pure unit testing with mocked dependencies for fast, deterministic execution.
 */
@ExtendWith(MockitoExtension.class)
class TranscriptionPollingSchedulerIntegrationTest {

    @Mock
    private LectureTranscriptionsRepositoryApi transcriptionRepositoryApi;

    @Mock
    private LectureTranscriptionService lectureTranscriptionService;

    @InjectMocks
    private NebulaTranscriptionPollingScheduler transcriptionPollingScheduler;

    private LectureTranscription transcription1;

    private LectureTranscription transcription2;

    @BeforeEach
    void setUp() {
        // Create test transcriptions - these represent what would be returned from the database
        transcription1 = createTranscription("job-1", TranscriptionStatus.PENDING);
        transcription2 = createTranscription("job-2", TranscriptionStatus.PENDING);
    }

    /**
     * Test that the scheduler correctly identifies and processes only PENDING transcriptions with non-null jobId.
     * Transcriptions without jobId or with COMPLETED status should be ignored.
     */
    @Test
    void pollPendingTranscriptions_callsServiceForEachPendingWithJobId() {
        // Mock repository to return only pending transcriptions with jobId
        List<LectureTranscription> pendingWithJobId = List.of(transcription1, transcription2);
        when(transcriptionRepositoryApi.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING)).thenReturn(pendingWithJobId);

        // Execute the scheduler method
        transcriptionPollingScheduler.pollPendingNebulaTranscriptions();

        // Verify the repository was queried correctly
        verify(transcriptionRepositoryApi).findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);

        // Verify only the two PENDING transcriptions with non-null jobId were processed
        ArgumentCaptor<LectureTranscription> captor = ArgumentCaptor.forClass(LectureTranscription.class);
        verify(lectureTranscriptionService, times(2)).processTranscription(captor.capture());

        // Verify the correct transcriptions were processed
        List<LectureTranscription> processed = captor.getAllValues();
        assertThat(processed).hasSize(2);
        assertThat(processed).extracting(LectureTranscription::getJobId).containsExactlyInAnyOrder("job-1", "job-2");
        assertThat(processed).allMatch(t -> t.getTranscriptionStatus() == TranscriptionStatus.PENDING);

        // Ensure no other interactions occurred
        verifyNoMoreInteractions(lectureTranscriptionService);
    }

    @Test
    void pollPendingTranscriptions_handlesEmptyList() {
        // Mock repository to return empty list
        when(transcriptionRepositoryApi.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING)).thenReturn(List.of());

        // Execute the scheduler method
        transcriptionPollingScheduler.pollPendingNebulaTranscriptions();

        // Verify the repository was queried
        verify(transcriptionRepositoryApi).findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);

        // Verify no transcriptions were processed
        verify(lectureTranscriptionService, times(0)).processTranscription(any());
        verifyNoMoreInteractions(lectureTranscriptionService);
    }

    /**
     * Helper method to create a test transcription with the given jobId and status.
     */
    private LectureTranscription createTranscription(String jobId, TranscriptionStatus status) {
        LectureTranscription transcription = new LectureTranscription();
        transcription.setJobId(jobId);
        transcription.setTranscriptionStatus(status);
        return transcription;
    }
}
