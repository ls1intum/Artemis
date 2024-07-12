package de.tum.in.www1.artemis.web.rest.open;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisStatusUpdateService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CourseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.ExerciseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for providing Pyris access to Artemis internal data and status updates.
 * All endpoints in this controller use custom token based authentication.
 * See {@link PyrisJobService#getAndAuthenticateJobFromHeaderElseThrow(HttpServletRequest)} for more information.
 */
@RestController
@Profile("iris")
@RequestMapping("api/public/pyris/")
public class PublicPyrisStatusUpdateResource {

    private final PyrisJobService pyrisJobService;

    private final PyrisStatusUpdateService pyrisStatusUpdateService;

    public PublicPyrisStatusUpdateResource(PyrisJobService pyrisJobService, PyrisStatusUpdateService pyrisStatusUpdateService) {
        this.pyrisJobService = pyrisJobService;
        this.pyrisStatusUpdateService = pyrisStatusUpdateService;
    }

    /**
     * POST public/pyris/pipelines/tutor-chat/runs/:runId/status : Set the status of an exercise chat job
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
    @PostMapping("pipelines/tutor-chat/runs/{runId}/status") // TODO: Rename this to 'exercise-chat' with next breaking Pyris version
    @EnforceNothing
    public ResponseEntity<Void> setStatusOfJob(@PathVariable String runId, @RequestBody PyrisChatStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof ExerciseChatJob exerciseChatJob)) {
            throw new ConflictException("Run ID is not a exercise chat job", "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(exerciseChatJob, statusUpdateDTO);

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
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request);
        if (!Objects.equals(job.jobId(), runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof CourseChatJob courseChatJob)) {
            throw new ConflictException("Run ID is not a course chat job", "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(courseChatJob, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST /api/public/pyris/webhooks/ingestion/runs/{runId}/status} : Set the status of an Ingestion job.
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
        PyrisJob job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request);
        if (!job.jobId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof IngestionWebhookJob ingestionWebhookJob)) {
            throw new ConflictException("Run ID is not an ingestion job", "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(ingestionWebhookJob, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }
}
