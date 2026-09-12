package de.tum.cit.aet.artemis.iris.web.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.Internal;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisPreparedLectureIngestionJobDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimResponseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatResponseDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;

/**
 * REST endpoints for the pull-based Pyris ingestion worker: the worker claims jobs when it has free
 * capacity and renews the lease of its running jobs on a fixed heartbeat interval. Together with the
 * lease reaper this replaces Artemis pushing work over a webhook and then inferring the worker's
 * liveness from the absence of progress callbacks.
 * <p>
 * Authentication: unlike the status update endpoints, no job token can exist before a claim, so these
 * endpoints authenticate with the shared Pyris secret ({@code artemis.iris.secret-token}) — the same
 * secret pair the two services already use in the other direction, compared in constant time.
 */
@Lazy
@FeatureUsage("internal/pyris-ingestion-worker")
@RestController
@Conditional(IrisEnabled.class)
@RequestMapping("api/iris/internal/ingestion/worker/")
public class PyrisInternalIngestionWorkerResource {

    private static final Logger log = LoggerFactory.getLogger(PyrisInternalIngestionWorkerResource.class);

    private final PyrisWebhookService pyrisWebhookService;

    private final Optional<ProcessingStateCallbackApi> processingStateCallbackApi;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    @Value("${artemis.iris.secret-token}")
    private String pyrisSecretToken;

    public PyrisInternalIngestionWorkerResource(PyrisWebhookService pyrisWebhookService, Optional<ProcessingStateCallbackApi> processingStateCallbackApi,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.pyrisWebhookService = pyrisWebhookService;
        this.processingStateCallbackApi = processingStateCallbackApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

    /**
     * {@code POST api/iris/internal/ingestion/worker/claim} : Claim pending ingestion jobs for a
     * pulling Pyris worker. Each claimed unit is prepared (job token registered, execution payload
     * assembled) and activated (phase transition, worker lease opened) before it is returned, so a
     * returned job is guaranteed to be authenticated and tracked. A claim whose response is lost on
     * the wire leaves a lease that is never renewed and is reclaimed by the lease reaper.
     *
     * @param request        the worker's claim: its boot id and free capacity
     * @param servletRequest the HTTP request, for secret authentication
     * @return the claimed jobs; empty when nothing is pending or the lecture module is disabled
     */
    @PostMapping("claim")
    @Internal
    public ResponseEntity<PyrisWorkerClaimResponseDTO> claimJobs(@RequestBody PyrisWorkerClaimRequestDTO request, HttpServletRequest servletRequest) {
        authenticate(servletRequest);
        if (processingStateCallbackApi.isEmpty() || lectureUnitRepositoryApi.isEmpty()) {
            return ResponseEntity.ok(new PyrisWorkerClaimResponseDTO(List.of()));
        }

        List<ClaimedIngestionUnitDTO> claims = processingStateCallbackApi.get().claimUnitsForWorker(request.bootId(), request.maxJobs());
        List<PyrisWebhookLectureIngestionExecutionDTO> jobs = new ArrayList<>();
        for (ClaimedIngestionUnitDTO claim : claims) {
            AttachmentVideoUnit unit;
            try {
                if (!(lectureUnitRepositoryApi.get().findByIdElseThrow(claim.lectureUnitId()) instanceof AttachmentVideoUnit attachmentVideoUnit)) {
                    continue;
                }
                unit = attachmentVideoUnit;
            }
            catch (EntityNotFoundException e) {
                // Deleted between claim and preparation; the cascade removes the state row as well.
                log.info("Claimed unit {} disappeared before preparation, skipping", claim.lectureUnitId());
                continue;
            }
            PyrisPreparedLectureIngestionJobDTO prepared = pyrisWebhookService.prepareLectureUnitIngestion(unit, claim.contentFingerprint(), claim.forceReingest());
            if (prepared == null) {
                processingStateCallbackApi.get().markClaimedUnitSkipped(claim.lectureUnitId());
                continue;
            }
            processingStateCallbackApi.get().activateClaimedJob(claim.lectureUnitId(), prepared.jobToken(), claim.targetPhase(), claim.contentFingerprint(), request.bootId());
            jobs.add(prepared.executionDTO());
        }
        return ResponseEntity.ok(new PyrisWorkerClaimResponseDTO(jobs));
    }

    /**
     * {@code POST api/iris/internal/ingestion/worker/heartbeat} : Renew the worker lease of every run
     * the worker is executing. The fixed cadence of this call, not pipeline progress, is what proves
     * the worker process is alive; the response lists runs Artemis no longer tracks so the worker can
     * stop executing them.
     *
     * @param request        the worker's boot id and its currently executing job tokens
     * @param servletRequest the HTTP request, for secret authentication
     * @return the tokens from the request that no longer belong to an in-flight run
     */
    @PostMapping("heartbeat")
    @Internal
    public ResponseEntity<PyrisWorkerHeartbeatResponseDTO> heartbeat(@RequestBody PyrisWorkerHeartbeatRequestDTO request, HttpServletRequest servletRequest) {
        authenticate(servletRequest);
        List<String> activeTokens = request.activeJobTokens() != null ? request.activeJobTokens() : List.of();
        if (processingStateCallbackApi.isEmpty()) {
            // Lecture module disabled: nothing the worker runs is tracked here, tell it to stop everything.
            return ResponseEntity.ok(new PyrisWorkerHeartbeatResponseDTO(activeTokens));
        }
        List<String> revoked = processingStateCallbackApi.get().renewWorkerLeases(request.bootId(), activeTokens);
        return ResponseEntity.ok(new PyrisWorkerHeartbeatResponseDTO(revoked));
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || pyrisSecretToken == null || !MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8), pyrisSecretToken.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessForbiddenException("Invalid worker authentication token");
        }
    }
}
