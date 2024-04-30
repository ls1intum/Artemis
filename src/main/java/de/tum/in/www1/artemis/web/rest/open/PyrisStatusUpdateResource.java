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
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

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

    @PostMapping("pipelines/tutor-chat/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setStatusOfTutorChatJob(@PathVariable String runId, @RequestBody PyrisStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        return setStatusOfJob(runId, statusUpdateDTO, request, TutorChatJob.class);
    }

    @PostMapping("webhooks/ingestion/runs/{runId}/status")
    @EnforceNothing
    public ResponseEntity<Void> setStatusOfIngestionJob(@PathVariable String runId, @RequestBody PyrisStatusUpdateDTO statusUpdateDTO, HttpServletRequest request) {
        return setStatusOfJob(runId, statusUpdateDTO, request, IngestionWebhookJob.class);
    }

    private <T extends PyrisJob> ResponseEntity<Void> setStatusOfJob(String runId, PyrisStatusUpdateDTO statusUpdateDTO, HttpServletRequest request, Class<T> jobClass) {
        var job = pyrisJobService.getJobFromHeader(request);
        if (!job.getId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match run ID in request body", "Job", "runIdMismatch");
        }
        if (!jobClass.isInstance(job)) {
            throw new ConflictException("Run ID is not of type " + jobClass.getSimpleName(), "Job", "invalidRunId");
        }

        pyrisStatusUpdateService.handleStatusUpdate(jobClass.cast(job), statusUpdateDTO);
        return ResponseEntity.ok().build();
    }
}
