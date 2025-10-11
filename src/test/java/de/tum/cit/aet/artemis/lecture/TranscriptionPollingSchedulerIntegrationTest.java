package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.lecture.service.TranscriptionPollingScheduler;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@TestPropertySource(properties = { "artemis.nebula.enabled=true" })
@ActiveProfiles({ "core", "scheduling" })
class TranscriptionPollingSchedulerIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "transcriptionpollingschedulertest";

    @Autowired
    private TranscriptionPollingScheduler transcriptionPollingScheduler;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private LectureTranscriptionService lectureTranscriptionService;

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
        // Spy the existing bean so we don’t trigger real HTTP in processTranscription
        LectureTranscriptionService spyService = spy(lectureTranscriptionService);
        doNothing().when(spyService).processTranscription(any(LectureTranscription.class));

        // Replace the field inside the scheduler bean
        ReflectionTestUtils.setField(transcriptionPollingScheduler, "transcriptionService", spyService);

        transcriptionPollingScheduler.pollPendingTranscriptions();

        // Verify only the PENDING with non-null jobId were processed
        List<LectureTranscription> pendingWithJob = lectureTranscriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);
        assertThat(pendingWithJob).hasSize(2);

        for (LectureTranscription t : pendingWithJob) {
            verify(spyService).processTranscription(t);
        }
        verifyNoMoreInteractions(spyService);
    }
}
