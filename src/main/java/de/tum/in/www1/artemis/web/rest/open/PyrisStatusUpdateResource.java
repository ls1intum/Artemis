package de.tum.in.www1.artemis.web.rest.open;

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
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for providing Pyris access to Artemis internal data
 */
@RestController
@Profile("iris")
@RequestMapping("api/public/pyris/")
public class PyrisStatusUpdateResource {

    private final PyrisJobService pyrisJobService;

    private final PyrisStatusUpdateService pyrisStatusUpdateService;

    public PyrisStatusUpdateResource(PyrisJobService pyrisJobService, PyrisStatusUpdateService pyrisStatusUpdateService) {
        this.pyrisJobService = pyrisJobService;
        this.pyrisStatusUpdateService = pyrisStatusUpdateService;
    }

    /**
     * {@code POST /api/public/pyris/pipelines/tutor-chat/runs/{runId}/status} : Set the status of a tutor chat job.
     *
     * @param runId           the ID of the job
     * @param statusUpdateDTO the status update
     * @param request         the HTTP request
     * @return a {@link ResponseEntity} with status {@code 200 (OK)}
     * @throws ConflictException        if the run ID in the URL does not match the run ID in the request body
     * @throws AccessForbiddenException if the token is invalid
     */
    @PostMapping("pipelines/tutor-chat/runs/{runId}/status")
    @EnforceNothing // We do token based authentication
    public ResponseEntity<Void> setStatusOfTutorChatJob(@PathVariable String runId, @RequestBody PyrisTutorChatStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        var job = pyrisJobService.getJobFromHeader(request);
        if (!job.getId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof TutorChatJob tutorChatJob)) {
            throw new ConflictException("Run ID is not a tutor chat job", "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(tutorChatJob, statusUpdateDTO);

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
        var job = pyrisJobService.getJobFromHeader(request);
        if (!job.getId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!(job instanceof IngestionWebhookJob ingestionWebhookJob)) {
            throw new ConflictException("Run ID is not an ingestion job", "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(ingestionWebhookJob, statusUpdateDTO);

        return ResponseEntity.ok().build();
    }

}
