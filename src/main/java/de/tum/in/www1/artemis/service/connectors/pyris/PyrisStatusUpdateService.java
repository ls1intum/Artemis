package de.tum.in.www1.artemis.service.connectors.pyris;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageState;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;

@Service
@Profile("iris")
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisChatSessionService irisChatSessionService;

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

        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state)
                .allMatch(state -> state == PyrisStageState.DONE || state == PyrisStageState.ERROR || state == PyrisStageState.SKIPPED);
        if (isDone) {
            pyrisJobService.removeJob(job.jobId());
        }
    }
}
