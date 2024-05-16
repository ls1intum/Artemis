package de.tum.in.www1.artemis.service.connectors.pyris;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.service.iris.session.IrisTutorChatSessionService;

@Service
@Profile("iris")
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisTutorChatSessionService irisTutorChatSessionService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisTutorChatSessionService irisTutorChatSessionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisTutorChatSessionService = irisTutorChatSessionService;
    }

    /**
     * Handles the status update of a tutor chat job and forwards it to {@link IrisTutorChatSessionService#handleStatusUpdate(TutorChatJob, PyrisTutorChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(TutorChatJob job, PyrisTutorChatStatusUpdateDTO statusUpdate) {
        irisTutorChatSessionService.handleStatusUpdate(job, statusUpdate);

        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state)
                .allMatch(state -> state == PyrisStageStateDTO.DONE || state == PyrisStageStateDTO.ERROR || state == PyrisStageStateDTO.SKIPPED);
        if (isDone) {
            pyrisJobService.removeJob(job.jobId());
        }
    }
}
