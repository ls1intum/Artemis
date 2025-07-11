package de.tum.cit.aet.artemis.iris.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisStatusUpdateService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck.PyrisConsistencyCheckStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.PyrisRewritingStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ConsistencyCheckJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.RewritingJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TextExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;

/**
 * REST controller for providing Pyris access to Artemis internal data and status updates.
 * All endpoints in this controller use custom token based authentication.
 * See {@link PyrisJobService#getAndAuthenticateJobFromHeaderElseThrow(HttpServletRequest, Class)} for more information.
 */
@Lazy
@RestController
@Profile(PROFILE_IRIS)
@RequestMapping("api/iris/public/pyris/")
public class PublicPyrisStatusUpdateResource {

    private final PyrisJobService pyrisJobService;

    private final PyrisStatusUpdateService pyrisStatusUpdateService;

    public PublicPyrisStatusUpdateResource(PyrisJobService pyrisJobService, PyrisStatusUpdateService pyrisStatusUpdateService) {
        this.pyrisJobService = pyrisJobService;
        this.pyrisStatusUpdateService = pyrisStatusUpdateService;
    }

    /**
     * POST public/pyris/pipelines/programming-exercise-chat/runs/:runId/status : Set the status of an exercise chat job
     * <p>
     * Uses custom token based authentication.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     */
    @PostMapping("pipelines/programming-exercise-chat/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setStatusOfJob(@PathVariable String runId, @RequestBody PyrisChatStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, ExerciseChatJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * POST public/pyris/pipelines/course-chat/runs/:runId/status : Set the status of a course chat job
     * <p>
     * Uses custom token based authentication.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     */
    @PostMapping("pipelines/course-chat/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setStatusOfCourseChatJob(@PathVariable String runId, @RequestBody PyrisChatStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, CourseChatJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * POST public/pyris/pipelines/competency-extraction/runs/:runId/status : Send the competencies extracted from a course description in a status update
     * <p>
     * Uses custom token based authentication.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     */
    @PostMapping("pipelines/competency-extraction/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setCompetencyExtractionJobStatus(@PathVariable String runId, @RequestBody PyrisCompetencyStatusUpdateDTO statusUpdateDTO,
            HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, CompetencyExtractionJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST public/pyris/pipelines/text-exercise-chat/runs/{runId}/status} : Set the status of a Text Exercise Chat job.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     */
    @PostMapping("pipelines/text-exercise-chat/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> respondInTextExerciseChat(@PathVariable String runId, @RequestBody PyrisTextExerciseChatStatusUpdateDTO statusUpdateDTO,
            HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, TextExerciseChatJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST public/pyris/pipelines/lecture-chat/runs/{runId}/status} : Set the status of a Lecture Chat job.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     */
    @PostMapping("pipelines/lecture-chat/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> respondInLectureChat(@PathVariable String runId, @RequestBody PyrisLectureChatStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, LectureChatJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * POST public/pyris/pipelines/rewriting/runs/:runId/status : Send the rewritten text in a status update
     * <p>
     * Uses custom token based authentication.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     */
    @PostMapping("pipelines/rewriting/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setRewritingJobStatus(@PathVariable String runId, @RequestBody PyrisRewritingStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, RewritingJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * POST public/pyris/pipelines/inconsistency-check/runs/:runId/status : Send the consistency check response in a status update
     * <p>
     * Uses custom token based authentication.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     */
    @PostMapping("pipelines/inconsistency-check/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setConsistencyCheckJobStatus(@PathVariable String runId, @RequestBody PyrisConsistencyCheckStatusUpdateDTO statusUpdateDTO,
            HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, ConsistencyCheckJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * POST public/pyris/pipelines/tutor-suggestion/runs/:runId/status : Send the tutor suggestion response in a status update
     * <p>
     * Uses custom token based authentication.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     */
    @PostMapping("pipelines/tutor-suggestion/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setTutorSuggestionJobStatus(@PathVariable String runId, @RequestBody TutorSuggestionStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, TutorSuggestionJob.class);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }

        pyrisStatusUpdateService.handleStatusUpdate(job, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST public/pyris/webhooks/ingestion/runs/{runId}/status} : Set the status of an Ingestion job.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     */
    @PostMapping("webhooks/ingestion/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setStatusOfIngestionJob(@PathVariable String runId, @RequestBody PyrisLectureIngestionStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        PyrisJob job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, PyrisJob.class);
        if (!job.jobId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof LectureIngestionWebhookJob lectureIngestionWebhookJob)) {
            throw new ConflictException("Run ID is not an ingestion job", "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(lectureIngestionWebhookJob, statusUpdateDTO);
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST public/pyris/webhooks/ingestion/faqs/runs/{runId}/status} : Set the status of an Ingestion job.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     */
    @PostMapping("webhooks/ingestion/faqs/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setStatusOfFaqIngestionJob(@PathVariable String runId, @RequestBody PyrisFaqIngestionStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        PyrisJob job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, PyrisJob.class);
        if (!job.jobId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof FaqIngestionWebhookJob faqIngestionWebhookJob)) {
            throw new ConflictException("Run ID is not an ingestion job", "Job", "invalidRunId");
        }
        pyrisStatusUpdateService.handleStatusUpdate(faqIngestionWebhookJob, statusUpdateDTO);
        return ResponseEntity.ok().build();
    }
}
