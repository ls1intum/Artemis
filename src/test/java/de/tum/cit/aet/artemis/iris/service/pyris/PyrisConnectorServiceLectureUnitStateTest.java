package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;

class PyrisConnectorServiceLectureUnitStateTest extends AbstractIrisIntegrationTest {

    private static final long COURSE_ID = 99L;

    private static final long LECTURE_ID = 42L;

    private static final long LECTURE_UNIT_ID = 7L;

    @Autowired
    private PyrisConnectorService pyrisConnectorService;

    @Autowired
    private PyrisJobService pyrisJobService;

    @AfterEach
    void cleanUpJobs() {
        pyrisJobService.currentJobs().stream().filter(job -> job instanceof LectureIngestionWebhookJob).toList().forEach(pyrisJobService::removeJob);
    }

    @Test
    void returnsDoneWhenPyrisReportsDone() throws Exception {
        irisRequestMockProvider.mockLectureUnitIngestionState(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID, IngestionState.DONE);

        var state = pyrisConnectorService.getLectureUnitIngestionState(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID);

        assertThat(state).isEqualTo(IngestionState.DONE);
    }

    @Test
    void returnsInProgressIfMatchingJobExists() throws Exception {
        irisRequestMockProvider.mockLectureUnitIngestionState(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID, IngestionState.NOT_STARTED);
        pyrisJobService.addLectureIngestionWebhookJob(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID);

        var state = pyrisConnectorService.getLectureUnitIngestionState(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID);

        assertThat(state).isEqualTo(IngestionState.IN_PROGRESS);
    }

    @Test
    void returnsRemoteStateWhenNoJobForUnit() throws Exception {
        irisRequestMockProvider.mockLectureUnitIngestionState(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID, IngestionState.PARTIALLY_INGESTED);

        var state = pyrisConnectorService.getLectureUnitIngestionState(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID);

        assertThat(state).isEqualTo(IngestionState.PARTIALLY_INGESTED);
    }

    @Test
    void throwsConnectorExceptionOnRestClientIssues() {
        irisRequestMockProvider.mockLectureUnitIngestionStateError(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID, HttpStatus.INTERNAL_SERVER_ERROR);

        assertThatThrownBy(() -> pyrisConnectorService.getLectureUnitIngestionState(COURSE_ID, LECTURE_ID, LECTURE_UNIT_ID)).isInstanceOf(PyrisConnectorException.class);
    }
}
