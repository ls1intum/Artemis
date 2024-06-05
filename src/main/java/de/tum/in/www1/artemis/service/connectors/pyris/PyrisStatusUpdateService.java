package de.tum.in.www1.artemis.service.connectors.pyris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageState;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;

@Service
@Profile("iris")
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisChatSessionService irisChatSessionService;

    private static final Logger log = LoggerFactory.getLogger(PyrisStatusUpdateService.class);

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisChatSessionService irisChatSessionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisChatSessionService = irisChatSessionService;
    }

    /**
     * Handles the status update of a tutor chat job and forwards it to {@link IrisChatSessionService#handleStatusUpdate(TutorChatJob, PyrisTutorChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(TutorChatJob job, PyrisTutorChatStatusUpdateDTO statusUpdate) {
        irisChatSessionService.handleStatusUpdate(job, statusUpdate);
        boolean isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state)
                .allMatch(state -> state == PyrisStageState.DONE || state == PyrisStageState.ERROR || state == PyrisStageState.SKIPPED);
        if (isDone) {
            pyrisJobService.removeJob(job.jobId());
        }
    }

    /**
     * Handles the status update of a lecture ingestion job and logs the results for now => will change later
     * TODO: Update this method to handle changes beyond logging
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(IngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate) {
        log.info("Ingestion job status updated with result: {}", statusUpdate.result());
        boolean isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state)
                .allMatch(state -> state == PyrisStageState.DONE || state == PyrisStageState.ERROR || state == PyrisStageState.SKIPPED);
        if (isDone) {
            pyrisJobService.removeJob(job.jobId());
        }
    }
}
