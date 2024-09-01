package de.tum.in.www1.artemis.service.connectors.pyris;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.domain.status.IngestionState;
import de.tum.in.www1.artemis.service.connectors.pyris.domain.status.PyrisStageState;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CompetencyExtractionJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CourseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.ExerciseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.iris.IrisCompetencyGenerationService;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;

@Service
@Profile("iris")
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final IrisCourseChatSessionService courseChatSessionService;

    private final IrisCompetencyGenerationService competencyGenerationService;

    private final AttachmentUnitRepository attachmentUnitRepository;

    private static final Logger log = LoggerFactory.getLogger(PyrisStatusUpdateService.class);

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisExerciseChatSessionService irisExerciseChatSessionService,
            IrisCourseChatSessionService courseChatSessionService, IrisCompetencyGenerationService competencyGenerationService, AttachmentUnitRepository attachmentUnitRepository) {
        this.pyrisJobService = pyrisJobService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.courseChatSessionService = courseChatSessionService;
        this.competencyGenerationService = competencyGenerationService;
        this.attachmentUnitRepository = attachmentUnitRepository;
    }

    /**
     * Handles the status update of a exercise chat job and forwards it to {@link IrisExerciseChatSessionService#handleStatusUpdate(ExerciseChatJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(ExerciseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        irisExerciseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminated(statusUpdate.stages(), job.jobId());
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

        removeJobIfTerminated(statusUpdate.stages(), job.jobId());
    }

    /**
     * Handles the status update of a competency extraction job and forwards it to
     * {@link de.tum.in.www1.artemis.service.iris.IrisCompetencyGenerationService#handleStatusUpdate(String, long, PyrisCompetencyStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CompetencyExtractionJob job, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        competencyGenerationService.handleStatusUpdate(job.userLogin(), job.courseId(), statusUpdate);

        removeJobIfTerminated(statusUpdate.stages(), job.jobId());
    }

    /**
     * Removes the job from the job service if the status update indicates that the job is terminated.
     * This is the case if all stages are in a terminal state.
     * <p>
     *
     * @see PyrisStageState#isTerminal()
     *
     * @param stages the stages of the status update
     * @param job    the job to remove
     */
    private boolean removeJobIfTerminated(List<PyrisStageDTO> stages, String job) {
        var isDone = stages.stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);
        if (isDone) {
            pyrisJobService.removeJob(job);
        }
        return isDone;
    }

    /**
     * Handles the status update of a lecture ingestion job and logs the results for now => will change later
     *
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(IngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate) {
        if (removeJobIfTerminated(statusUpdate.stages(), job.jobId())) {
            attachmentUnitRepository.findById(statusUpdate.id()).ifPresent(unit -> {
                PyrisStageState lastState = statusUpdate.stages().getLast().state();

                if (lastState == PyrisStageState.DONE) {
                    unit.setPyrisIngestionState(IngestionState.DONE);
                }
                else if (lastState == PyrisStageState.ERROR || lastState == PyrisStageState.SKIPPED) {
                    unit.setPyrisIngestionState(IngestionState.ERROR);
                }

                attachmentUnitRepository.save(unit);
            });
        }
    }

}
