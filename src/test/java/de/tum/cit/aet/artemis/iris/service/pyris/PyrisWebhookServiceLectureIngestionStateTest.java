package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class PyrisWebhookServiceLectureIngestionStateTest extends AbstractIrisIntegrationTest {

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

    private Lecture lecture;

    @BeforeEach
    void setUp() {
        lecture = lectureUtilService.createCourseWithLecture(true);
        AttachmentVideoUnit firstUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        AttachmentVideoUnit secondUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        lecture = lectureUtilService.addLectureUnitsToLecture(lecture, List.of(firstUnit, secondUnit));
        lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId());
    }

    private IngestionState getLectureState(IngestionState firstUnitState, IngestionState secondUnitState) throws Exception {
        List<AttachmentVideoUnit> units = lecture.getLectureUnits().stream().map(AttachmentVideoUnit.class::cast).toList();
        irisRequestMockProvider.mockLectureUnitIngestionState(lecture.getCourse().getId(), lecture.getId(), units.get(0).getId(), firstUnitState);
        irisRequestMockProvider.mockLectureUnitIngestionState(lecture.getCourse().getId(), lecture.getId(), units.get(1).getId(), secondUnitState);

        Map<Long, IngestionState> states = pyrisWebhookService.getLecturesIngestionState(lecture.getCourse().getId());
        return states.get(lecture.getId());
    }

    @Test
    void returnsDoneWhenAllUnitsDone() throws Exception {
        IngestionState state = getLectureState(IngestionState.DONE, IngestionState.DONE);

        assertThat(state).isEqualTo(IngestionState.DONE);
    }

    @Test
    void returnsNotStartedWhenAllUnitsNotStarted() throws Exception {
        IngestionState state = getLectureState(IngestionState.NOT_STARTED, IngestionState.NOT_STARTED);

        assertThat(state).isEqualTo(IngestionState.NOT_STARTED);
    }

    @Test
    void returnsErrorWhenAllUnitsErrored() throws Exception {
        IngestionState state = getLectureState(IngestionState.ERROR, IngestionState.ERROR);

        assertThat(state).isEqualTo(IngestionState.ERROR);
    }

    @Test
    void returnsPartiallyIngestedWhenAnyUnitProgressed() throws Exception {
        IngestionState state = getLectureState(IngestionState.DONE, IngestionState.ERROR);

        assertThat(state).isEqualTo(IngestionState.PARTIALLY_INGESTED);
    }

    @Test
    void returnsNotStartedForMixedStatesWithoutProgress() throws Exception {
        IngestionState state = getLectureState(IngestionState.NOT_STARTED, IngestionState.ERROR);

        assertThat(state).isEqualTo(IngestionState.NOT_STARTED);
    }
}
