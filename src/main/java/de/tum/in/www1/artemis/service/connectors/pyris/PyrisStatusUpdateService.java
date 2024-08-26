package de.tum.in.www1.artemis.service.connectors.pyris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageState;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CourseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.ExerciseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;

@Service
@Profile("iris")
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final IrisCourseChatSessionService courseChatSessionService;

    private static final Logger log = LoggerFactory.getLogger(PyrisStatusUpdateService.class);

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisExerciseChatSessionService irisExerciseChatSessionService,
            IrisCourseChatSessionService courseChatSessionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.courseChatSessionService = courseChatSessionService;
    }

    /**
     * Handles the status update of a exercise chat job and forwards it to {@link IrisExerciseChatSessionService#handleStatusUpdate(ExerciseChatJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(ExerciseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        irisExerciseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminated(statusUpdate, job.jobId());
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

        removeJobIfTerminated(statusUpdate, job.jobId());
    }

    /**
     * Removes the job from the job service if the status update indicates that the job is terminated.
     * This is the case if all stages are in a terminal state.
     * <p>
     *
     * @see PyrisStageState#isTerminal()
     *
     * @param statusUpdate the status update
     * @param job          the job to remove
     */
    private void removeJobIfTerminated(PyrisChatStatusUpdateDTO statusUpdate, String job) {
        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);
        if (isDone) {
            pyrisJobService.removeJob(job);
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
        statusUpdate.stages().forEach(stage -> log.info(stage.name() + ":" + stage.message()));
        boolean isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state)
                .allMatch(state -> state == PyrisStageState.DONE || state == PyrisStageState.ERROR || state == PyrisStageState.SKIPPED);
        if (isDone) {
            pyrisJobService.removeJob(job.jobId());
        }
    }
}
