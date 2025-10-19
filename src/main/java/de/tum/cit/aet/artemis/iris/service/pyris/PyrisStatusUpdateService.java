package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.IrisRewritingService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.PyrisRewritingStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.RewritingJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TextExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTextExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;

@Lazy
@Service
@Profile(PROFILE_IRIS)
public class PyrisStatusUpdateService {

    private final PyrisJobService pyrisJobService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final IrisTextExerciseChatSessionService irisTextExerciseChatSessionService;

    private final IrisCourseChatSessionService courseChatSessionService;

    private final IrisCompetencyGenerationService competencyGenerationService;

    private final IrisRewritingService rewritingService;

    private final IrisLectureChatSessionService irisLectureChatSessionService;

    private final IrisTutorSuggestionSessionService irisTutorSuggestionSessionService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisExerciseChatSessionService irisExerciseChatSessionService,
            IrisTextExerciseChatSessionService irisTextExerciseChatSessionService, IrisCourseChatSessionService courseChatSessionService,
            IrisCompetencyGenerationService competencyGenerationService, IrisLectureChatSessionService irisLectureChatSessionService, IrisRewritingService rewritingService,
            IrisTutorSuggestionSessionService irisTutorSuggestionSessionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.irisTextExerciseChatSessionService = irisTextExerciseChatSessionService;
        this.courseChatSessionService = courseChatSessionService;
        this.competencyGenerationService = competencyGenerationService;
        this.irisLectureChatSessionService = irisLectureChatSessionService;
        this.rewritingService = rewritingService;
        this.irisTutorSuggestionSessionService = irisTutorSuggestionSessionService;
    }

    /**
     * Handles the status update of a exercise chat job and forwards it to
     * {@link IrisExerciseChatSessionService#handleStatusUpdate(TrackedSessionBasedPyrisJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(ExerciseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var updatedJob = irisExerciseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of an exercise chat job and forwards it to
     * {@link IrisTextExerciseChatSessionService#handleStatusUpdate(TextExerciseChatJob, PyrisTextExerciseChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(TextExerciseChatJob job, PyrisTextExerciseChatStatusUpdateDTO statusUpdate) {
        var updatedJob = irisTextExerciseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of a course chat job and forwards it to
     * {@link de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService#handleStatusUpdate(TrackedSessionBasedPyrisJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CourseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var updatedJob = courseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of a competency extraction job and forwards it to
     * {@link IrisCompetencyGenerationService#handleStatusUpdate(CompetencyExtractionJob, PyrisCompetencyStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CompetencyExtractionJob job, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        var updatedJob = competencyGenerationService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of a rewriting job and forwards it to
     * {@link IrisRewritingService#handleStatusUpdate(RewritingJob, PyrisRewritingStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(RewritingJob job, PyrisRewritingStatusUpdateDTO statusUpdate) {
        var updatedJob = rewritingService.handleStatusUpdate(job, statusUpdate);
        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Removes the job from the job service if the status update indicates that the job is terminated; updates it to distribute changes otherwise.
     * A job is terminated if all stages are in a terminal state.
     * <p>
     *
     * @see PyrisStageState#isTerminal()
     *
     * @param stages the stages of the status update
     * @param job    the job to remove or to update
     */
    private void removeJobIfTerminatedElseUpdate(List<PyrisStageDTO> stages, PyrisJob job) {
        var isDone = stages.stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);
        if (isDone) {
            pyrisJobService.removeJob(job);
        }
        else {
            pyrisJobService.updateJob(job);
        }
    }

    /**
     * Handles the status update of a lecture ingestion job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate) {
        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), job);
    }

    /**
     * Handles the status update of a Lecture Chat job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(LectureChatJob job, PyrisLectureChatStatusUpdateDTO statusUpdate) {
        var updatedJob = irisLectureChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of a FAQ ingestion job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(FaqIngestionWebhookJob job, PyrisFaqIngestionStatusUpdateDTO statusUpdate) {
        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), job);
    }

    /**
     * Handles the status update of a tutor suggestion job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update received
     */
    public void handleStatusUpdate(TutorSuggestionJob job, TutorSuggestionStatusUpdateDTO statusUpdate) {
        var updatedJob = irisTutorSuggestionSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

}
