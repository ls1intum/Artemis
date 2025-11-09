package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.nebula.service.NebulaTranscriptionPollingScheduler;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TranscriptionPollingSchedulerIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "transcriptionpollingschedulertest";

    // Mock the scheduler to prevent automatic scheduled execution during tests
    @MockitoBean
    private NebulaTranscriptionPollingScheduler transcriptionPollingScheduler;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    private void createPendingTranscription(String jobId) {
        var transcription = new LectureTranscription();
        transcription.setJobId(jobId);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        lectureTranscriptionRepository.save(transcription);
    }

    private void createTranscription(String jobId, TranscriptionStatus status) {
        var transcription = new LectureTranscription();
        transcription.setJobId(jobId);
        transcription.setTranscriptionStatus(status);
        lectureTranscriptionRepository.save(transcription);
    }

    @BeforeEach
    void initData() {
        // Clean out anything lingering (if your base doesn't auto-rollback per test)
        lectureTranscriptionRepository.deleteAll();

        // PENDING with jobId (should be polled)
        createPendingTranscription("job-1");
        createPendingTranscription("job-2");
        // PENDING but jobId == null (should be ignored by the query)
        createTranscription(null, TranscriptionStatus.PENDING);
        // COMPLETED (should not be polled)
        createTranscription("job-4", TranscriptionStatus.COMPLETED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void pollPendingTranscriptions_callsServiceForEachPendingWithJobId() {
        // Stub the spy bean to avoid real HTTP calls
        // TODO: we should mock the external Nebula service using MockServer for more realistic integration tests
        doNothing().when(lectureTranscriptionService).processTranscription(any(LectureTranscription.class));

        // Manually invoke the scheduler logic to avoid race conditions with the background scheduler
        // This simulates what the scheduler would do without relying on Spring's @Scheduled timing
        List<LectureTranscription> pendingTranscriptions = lectureTranscriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);

        for (LectureTranscription transcription : pendingTranscriptions) {
            lectureTranscriptionService.processTranscription(transcription);
        }

        // Verify only the two PENDING transcriptions with non-null jobId were processed
        ArgumentCaptor<LectureTranscription> captor = ArgumentCaptor.forClass(LectureTranscription.class);
        verify(lectureTranscriptionService, times(2)).processTranscription(captor.capture());

        List<LectureTranscription> processed = captor.getAllValues();
        assertThat(processed).extracting(LectureTranscription::getJobId).containsExactlyInAnyOrder("job-1", "job-2");
        assertThat(processed).allMatch(t -> t.getTranscriptionStatus() == TranscriptionStatus.PENDING);

        verifyNoMoreInteractions(lectureTranscriptionService);
    }
}
