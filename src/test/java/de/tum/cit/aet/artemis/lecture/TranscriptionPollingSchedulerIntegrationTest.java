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

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.service.TranscriptionPollingScheduler;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TranscriptionPollingSchedulerIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "transcriptionpollingschedulertest";

    @Autowired
    private TranscriptionPollingScheduler transcriptionPollingScheduler;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    private LectureTranscription createPendingTranscription(String jobId) {
        var transcription = new LectureTranscription();
        transcription.setJobId(jobId);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        return lectureTranscriptionRepository.save(transcription);
    }

    private LectureTranscription createTranscription(String jobId, TranscriptionStatus status) {
        var transcription = new LectureTranscription();
        transcription.setJobId(jobId);
        transcription.setTranscriptionStatus(status);
        return lectureTranscriptionRepository.save(transcription);
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
        doNothing().when(lectureTranscriptionService).processTranscription(any(LectureTranscription.class));

        transcriptionPollingScheduler.pollPendingTranscriptions();

        // Verify only the two PENDING transcriptions with non-null jobId were processed
        ArgumentCaptor<LectureTranscription> captor = ArgumentCaptor.forClass(LectureTranscription.class);
        verify(lectureTranscriptionService, times(2)).processTranscription(captor.capture());

        List<LectureTranscription> processed = captor.getAllValues();
        assertThat(processed).extracting(LectureTranscription::getJobId).containsExactlyInAnyOrder("job-1", "job-2");
        assertThat(processed).allMatch(t -> t.getTranscriptionStatus() == TranscriptionStatus.PENDING);

        verifyNoMoreInteractions(lectureTranscriptionService);
    }
}
