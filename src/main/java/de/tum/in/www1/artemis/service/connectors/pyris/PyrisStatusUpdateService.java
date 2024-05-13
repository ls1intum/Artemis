package de.tum.in.www1.artemis.service.connectors.pyris;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CourseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisTutorChatSessionService;

@Service
@Profile("iris")
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisTutorChatSessionService irisTutorChatSessionService;

    private final IrisCourseChatSessionService courseChatSessionService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisTutorChatSessionService irisTutorChatSessionService,
            IrisCourseChatSessionService courseChatSessionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisTutorChatSessionService = irisTutorChatSessionService;
        this.courseChatSessionService = courseChatSessionService;
    }

    /**
     * Handles the status update of a tutor chat job and forwards it to {@link IrisTutorChatSessionService#handleStatusUpdate(TutorChatJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(TutorChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        irisTutorChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfJobTerminated(statusUpdate, job.jobId());
    }

    /**
     * Handles the status update of a course chat job and forwards it to
     * {@link de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService#handleStatusUpdate(CourseChatJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CourseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        courseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfJobTerminated(statusUpdate, job.jobId());
    }

    private void removeJobIfJobTerminated(PyrisChatStatusUpdateDTO statusUpdate, String job) {
        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state).allMatch(PyrisStageStateDTO::isTerminal);
        if (isDone) {
            pyrisJobService.removeJob(job);
        }
    }
}
