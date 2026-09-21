package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * The backfill that creates Iris synchronization state must only pick up lecture units Pyris can actually hold.
 *
 * <p>
 * Pyris answers a synchronization for anything it has not ingested with 404, and the retry path used to treat that as a
 * transient failure, so every unit the backfill created a state for without an ingestion behind it was pushed once an
 * hour for as long as its course stayed active.
 */
class IrisLectureUnitSyncBackfillQueryTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private LectureUnitProcessingStateRepository processingStateRepository;

    @Test
    void backfillOnlyPicksUpUnitsWhoseIngestionFinished() {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        AttachmentVideoUnit neverProcessed = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        AttachmentVideoUnit stillIngesting = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        // A unit carrying only a video is also the case the implicit join on the attachment used to drop.
        AttachmentVideoUnit ingested = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(neverProcessed, stillIngesting, ingested));
        savePhase(stillIngesting, ProcessingPhase.INGESTING);
        savePhase(ingested, ProcessingPhase.DONE);

        var found = attachmentVideoUnitRepository.findUnitsMissingIrisSyncStateFromActiveCourses(ZonedDateTime.now(), PageRequest.of(0, 50));

        assertThat(found).extracting(AttachmentVideoUnit::getId).contains(ingested.getId()).doesNotContain(neverProcessed.getId(), stillIngesting.getId());
    }

    private void savePhase(AttachmentVideoUnit unit, ProcessingPhase phase) {
        LectureUnitProcessingState state = new LectureUnitProcessingState(unit);
        state.transitionTo(phase);
        processingStateRepository.save(state);
    }
}
