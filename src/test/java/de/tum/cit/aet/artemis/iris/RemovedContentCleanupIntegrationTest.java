package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureContentProcessingService;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

/**
 * The cleanup after a unit lost its last processable content, through the real service and repositories: an Iris outage keeps the unit's
 * markers and the cleanup's claim, a later attempt cannot take a recent claim over, and once the claim is stale the next attempt takes it
 * over and settles the unit as nothing indexed.
 */
class RemovedContentCleanupIntegrationTest extends AbstractIrisIntegrationTest {

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureContentProcessingService processingService;

    @Autowired
    private LectureUnitProcessingStateRepository processingStateRepository;

    @Autowired
    private AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private AttachmentVideoUnit unit;

    private LectureUnitProcessingState state;

    @BeforeEach
    void initTestCase() {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        activateIrisFor(lecture.getCourse());
        // The unit had a video that was removed; its state still records the video and the confirmed index content
        AttachmentVideoUnit created = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        created.setVideoSource(null);
        attachmentVideoUnitRepository.save(created);
        unit = attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(created.getId()).orElseThrow();
        state = new LectureUnitProcessingState(unit);
        state.setPhase(ProcessingPhase.DONE);
        state.setVideoSourceHash("removed-video-hash");
        state.setConfirmedFingerprint("v1:old");
        state = processingStateRepository.save(state);
    }

    @Test
    void testCleanupSurvivesAnIrisOutageAndIsTakenOverOnceItsClaimIsStale() {
        // Iris is down for the first delete and back for the next one; the mock server takes both answers up front
        irisRequestMockProvider.mockDeletionWebhookFailure();
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
        });

        assertThat(processingService.retryRemovedContentCleanup(unit, state.getId())).as("the first attempt claims the cleanup").isTrue();
        LectureUnitProcessingState afterOutage = processingStateRepository.findById(state.getId()).orElseThrow();
        assertThat(afterOutage.getVideoSourceHash()).as("a failed Iris delete keeps the markers for a retry").isEqualTo("removed-video-hash");
        assertThat(afterOutage.getClaimToken()).as("and keeps the cleanup's claim").isNotNull();

        assertThat(processingService.retryRemovedContentCleanup(unit, state.getId())).as("a recent claim is not taken over").isFalse();

        // The claim ages past the takeover cutoff, as when the node that held it died
        afterOutage.setLastUpdated(ZonedDateTime.now().minusMinutes(30));
        processingStateRepository.save(afterOutage);

        assertThat(processingService.retryRemovedContentCleanup(unit, state.getId())).as("a stale claim is taken over").isTrue();
        LectureUnitProcessingState settled = processingStateRepository.findById(state.getId()).orElseThrow();
        assertThat(settled.getPhase()).isEqualTo(ProcessingPhase.DONE);
        assertThat(settled.getVideoSourceHash()).as("nothing is indexed any more").isNull();
        assertThat(settled.getConfirmedFingerprint()).isNull();
        assertThat(settled.getClaimToken()).isNull();
    }
}
