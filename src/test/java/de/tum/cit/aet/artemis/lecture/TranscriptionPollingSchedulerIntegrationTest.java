package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.lecture.service.TranscriptionPollingScheduler;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TranscriptionPollingSchedulerIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "transcriptionpollingschedulertest";

    @MockitoBean
    private TranscriptionPollingScheduler transcriptionPollingScheduler;

    @MockitoBean
    private LectureTranscriptionService lectureTranscriptionService;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @BeforeEach
    void initData() {
        // Clean out anything lingering (if your base doesn’t auto-rollback per test)
        lectureTranscriptionRepository.deleteAll();

        // PENDING with jobId (should be polled)
        var t1 = new LectureTranscription();
        t1.setJobId("job-1");
        t1.setTranscriptionStatus(TranscriptionStatus.PENDING);
        lectureTranscriptionRepository.save(t1);

        var t2 = new LectureTranscription();
        t2.setJobId("job-2");
        t2.setTranscriptionStatus(TranscriptionStatus.PENDING);
        lectureTranscriptionRepository.save(t2);

        // PENDING but jobId == null (should be ignored by the query)
        var t3 = new LectureTranscription();
        t3.setJobId(null);
        t3.setTranscriptionStatus(TranscriptionStatus.PENDING);
        lectureTranscriptionRepository.save(t3);

        // COMPLETED (should not be polled)
        var t4 = new LectureTranscription();
        t4.setJobId("job-4");
        t4.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
        lectureTranscriptionRepository.save(t4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void pollPendingTranscriptions_callsServiceForEachPendingWithJobId() {
        // Mock the scheduler to simulate calling the service for each pending transcription
        doAnswer(invocation -> {
            List<LectureTranscription> pendingWithJob = lectureTranscriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);
            for (LectureTranscription t : pendingWithJob) {
                lectureTranscriptionService.processTranscription(t);
            }
            return null;
        }).when(transcriptionPollingScheduler).pollPendingTranscriptions();

        transcriptionPollingScheduler.pollPendingTranscriptions();

        // Verify only the PENDING with non-null jobId were processed
        List<LectureTranscription> pendingWithJob = lectureTranscriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);
        assertThat(pendingWithJob).hasSize(2);

        for (LectureTranscription t : pendingWithJob) {
            verify(lectureTranscriptionService).processTranscription(t);
        }
        verifyNoMoreInteractions(lectureTranscriptionService);
    }
}
