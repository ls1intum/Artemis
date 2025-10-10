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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.lecture.service.TranscriptionPollingScheduler;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@TestPropertySource(properties = { "artemis.nebula.enabled=true" })
class TranscriptionPollingSchedulerIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "transcriptionpollingschedulertest";

    @Autowired
    private TranscriptionPollingScheduler transcriptionPollingScheduler;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private LectureTranscriptionService lectureTranscriptionService;

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
