package de.tum.in.www1.artemis.web.rest.open;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisStatusUpdateService;
import de.tum.in.www1.artemis.service.connectors.pyris.domain.status.IngestionState;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CompetencyExtractionJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CourseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.ExerciseChatJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for providing Pyris access to Artemis internal data and status updates.
 * All endpoints in this controller use custom token based authentication.
 * See {@link PyrisJobService#getAndAuthenticateJobFromHeaderElseThrow(HttpServletRequest, Class)} for more information.
 */
@RestController
@Profile("iris")
@RequestMapping("api/public/pyris/")
public class PublicPyrisStatusUpdateResource {

    private final PyrisJobService pyrisJobService;

    private final PyrisStatusUpdateService pyrisStatusUpdateService;

    private final PyrisConnectorService pyrisConnectorService;

    private static final Logger log = LoggerFactory.getLogger(PublicPyrisStatusUpdateResource.class);

    public PublicPyrisStatusUpdateResource(PyrisJobService pyrisJobService, PyrisStatusUpdateService pyrisStatusUpdateService, PyrisConnectorService pyrisConnectorService) {
        this.pyrisJobService = pyrisJobService;
        this.pyrisStatusUpdateService = pyrisStatusUpdateService;
        this.pyrisConnectorService = pyrisConnectorService;
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
        PyrisJob job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, PyrisJob.class);
        if (!job.jobId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof IngestionWebhookJob ingestionWebhookJob)) {
            throw new ConflictException("Run ID is not an ingestion job", "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(ingestionWebhookJob, statusUpdateDTO);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves the overall ingestion state of a lecture by communicating with Pyris.
     *
     * <p>
     * This method sends a GET request to the external Pyris service to fetch the current ingestion
     * state of a lecture, identified by its `lectureId`. The ingestion state can be aggregated from
     * multiple lecture units or can reflect the overall status of the lecture ingestion process.
     * </p>
     *
     * @param lectureId the ID of the lecture for which the ingestion state is being requested
     * @param courseId  the ID of the lecture for which the ingestion state is being requested
     * @return a {@link ResponseEntity} containing the {@link IngestionState} of the lecture,
     */
    @GetMapping("courses/{courseId}/lectures/{lectureId}/ingestion-state")
    @EnforceNothing
    public ResponseEntity<IngestionState> getStatusOfLectureIngestion(@PathVariable long courseId, @PathVariable long lectureId) {
        try {
            IngestionState state = pyrisConnectorService.getLectureIngestionState(courseId, lectureId);
            return ResponseEntity.ok(state);
        }
        catch (Exception e) {
            log.error("Error fetching ingestion state for lecture {}", lectureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves the ingestion state of a specific lecture unit by communicating with Pyris.
     *
     * <p>
     * This method sends a GET request to the external Pyris service to fetch the current ingestion
     * state of a lecture unit, identified by its ID. It constructs a request using the provided
     * `lectureId` and `lectureUnitId` and returns the state of the ingestion process (e.g., NOT_STARTED,
     * IN_PROGRESS, DONE, ERROR).
     * </p>
     *
     * @param courseId      the ID of the lecture the unit belongs to
     * @param lectureId     the ID of the lecture the unit belongs to
     * @param lectureUnitId the ID of the lecture unit for which the ingestion state is being requested
     * @return a {@link ResponseEntity} containing the {@link IngestionState} of the lecture unit,
     */
    @GetMapping("courses/{courseId}/lectures/{lectureId}/lecture-units/{lectureUnitId}/ingestion-state")
    @EnforceNothing
    public ResponseEntity<IngestionState> getStatusOfLectureUnitIngestion(@PathVariable long courseId, @PathVariable long lectureId, @PathVariable long lectureUnitId) {
        try {
            IngestionState state = pyrisConnectorService.getLectureUnitIngestionState(courseId, lectureId, lectureUnitId);
            return ResponseEntity.ok(state);
        }
        catch (Exception e) {
            log.error("Error fetching ingestion state for lecture unit {} in lecture {}", lectureUnitId, lectureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
