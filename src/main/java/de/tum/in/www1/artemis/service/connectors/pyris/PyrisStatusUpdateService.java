package de.tum.in.www1.artemis.service.connectors.pyris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;

@Service
@Profile("iris")
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisChatSessionService irisChatSessionService;

    private static final Logger logger = LoggerFactory.getLogger(PyrisStatusUpdateService.class);

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisChatSessionService irisChatSessionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisChatSessionService = irisChatSessionService;
    }

    /**
     * Handles the status update of a tutor chat job and forwards it to {@link IrisChatSessionService#handleStatusUpdate(TutorChatJob, PyrisStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(PyrisJob job, PyrisStatusUpdateDTO statusUpdate) {
        if (job instanceof TutorChatJob) {
            irisChatSessionService.handleStatusUpdate((TutorChatJob) job, statusUpdate);
        }
        else if (job instanceof IngestionWebhookJob ingestionJob) {
            logger.info("Ingestion job status updated with result: {}", ingestionJob.getId());
        }
        else {
            logger.error("Unsupported job type or mismatch in DTO type");
            throw new IllegalArgumentException("Unsupported job type or mismatch in DTO type");
        }
        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state)
                .allMatch(state -> state == PyrisStageStateDTO.DONE || state == PyrisStageStateDTO.ERROR || state == PyrisStageStateDTO.SKIPPED);
        if (isDone) {
            pyrisJobService.removeJob(job.getId());
        }
    }
}
