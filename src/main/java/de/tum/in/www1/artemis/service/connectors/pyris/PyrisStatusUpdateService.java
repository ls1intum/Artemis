package de.tum.in.www1.artemis.service.connectors.pyris;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;

@Service
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisChatSessionService irisChatSessionService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisChatSessionService irisChatSessionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisChatSessionService = irisChatSessionService;
    }

    public void handleStatusUpdate(TutorChatJob job, PyrisTutorChatStatusUpdateDTO statusUpdate) {
        irisChatSessionService.handleStatusUpdate(job, statusUpdate);

        var isDone = statusUpdate.getStages().stream().map(PyrisStageDTO::getState)
                .allMatch(state -> state == PyrisStageStateDTO.DONE || state == PyrisStageStateDTO.ERROR || state == PyrisStageStateDTO.SKIPPED);
        if (isDone) {
            pyrisJobService.removeJob(job.getId());
        }
    }
}
