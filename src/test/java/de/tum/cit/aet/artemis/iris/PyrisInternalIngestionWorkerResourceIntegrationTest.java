package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisCallbackTokenService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimResponseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatResponseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJobType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.service.ProcessingStateRecoveryService;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

/**
 * End-to-end HTTP tests for the pull-based Pyris ingestion worker endpoints ({@code claim} and {@code heartbeat}),
 * which authenticate with the shared Pyris secret instead of a user or a job token.
 * <p>
 * The claim path needs no outgoing call to Pyris: the job is handed to the worker in the response, so there is no
 * webhook to mock. The processing state table is shared with the rest of the suite, so the setup clears it; an
 * IDLE row left behind by another test class would otherwise be claimed together with the seeded one.
 */
class PyrisInternalIngestionWorkerResourceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisingestionworkerresource";

    private static final String CLAIM_URL = "/api/iris/internal/ingestion/worker/claim";

    private static final String HEARTBEAT_URL = "/api/iris/internal/ingestion/worker/heartbeat";

    private static final String BOOT_ID = TEST_PREFIX + "-boot-1";

    @Value("${artemis.iris.secret-token}")
    private String pyrisSecretToken;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureUnitProcessingStateRepository lectureUnitProcessingStateRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private PyrisCallbackTokenService callbackTokenService;

    @Autowired
    private ProcessingStateRecoveryService processingStateRecoveryService;

    private AttachmentVideoUnit unit;

    @BeforeEach
    void initTestCase() {
        featureToggleService.enableFeature(Feature.LectureContentProcessing);
        lectureUnitProcessingStateRepository.deleteAll();

        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        activateIrisFor(lecture.getCourse());
        // A stored PDF and no video: the claim picks INGESTING and the preparation reads the PDF from the file store
        unit = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 0, true);
        lectureUnitProcessingStateRepository.save(new LectureUnitProcessingState(unit));
    }

    private HttpHeaders workerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // The worker sends the shared secret as is, without a scheme prefix
        headers.set(HttpHeaders.AUTHORIZATION, pyrisSecretToken);
        return headers;
    }

    private HttpHeaders wrongSecretHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, pyrisSecretToken + "-wrong");
        return headers;
    }

    private LectureUnitProcessingState reloadState() {
        return lectureUnitProcessingStateRepository.findByLectureUnit_Id(unit.getId()).orElseThrow();
    }

    /**
     * Claims the seeded unit through the endpoint and returns its in-flight state.
     */
    private LectureUnitProcessingState claimSeededUnit() throws Exception {
        PyrisWorkerClaimResponseDTO response = request.postWithResponseBody(CLAIM_URL, new PyrisWorkerClaimRequestDTO(BOOT_ID, 5), PyrisWorkerClaimResponseDTO.class, HttpStatus.OK,
                workerHeaders());
        assertThat(response).as("the claim must answer with a body").isNotNull();
        assertThat(response.jobs()).as("exactly the seeded unit must be handed to the worker").hasSize(1);
        return reloadState();
    }

    @Test
    void claimWithCorrectSecretHandsOutOneJobAndActivatesIt() throws Exception {
        LectureUnitProcessingState state = claimSeededUnit();

        assertThat(state.getPhase()).as("a claimed unit with a PDF and no video must be in flight").isIn(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING);
        assertThat(state.getIngestionJobToken()).as("activation must register a job token").isNotNull().isNotBlank();
        assertThat(state.getLastHeartbeatAt()).as("activation must open the worker lease").isNotNull();
        assertThat(state.getLockedBy()).as("the claiming worker must hold the lease").isEqualTo(BOOT_ID);
    }

    @Test
    void claimHandsOutAPlainRunIdAndASignedTokenForIt() throws Exception {
        PyrisWorkerClaimResponseDTO response = request.postWithResponseBody(CLAIM_URL, new PyrisWorkerClaimRequestDTO(BOOT_ID, 5), PyrisWorkerClaimResponseDTO.class, HttpStatus.OK,
                workerHeaders());
        assertThat(response).isNotNull();
        assertThat(response.jobs()).hasSize(1);
        var settings = response.jobs().getFirst().settings();

        assertThat(UUID.fromString(settings.runId()).toString()).as("the run id is a plain UUID").isEqualTo(settings.runId());
        assertThat(reloadState().getIngestionJobToken()).as("the processing state fences on the run id").isEqualTo(settings.runId());
        assertThat(callbackTokenService.verify(settings.authenticationToken())).as("the token is a callback token for exactly this run")
                .contains(new PyrisCallbackTokenService.VerifiedCallback(settings.runId(), PyrisJobType.LECTURE_INGESTION));
    }

    @Test
    void heartbeatWithClaimedRunIdRenewsTheLease() throws Exception {
        LectureUnitProcessingState claimed = claimSeededUnit();
        String runId = claimed.getIngestionJobToken();

        // Age the lease so that the renewal is observable without waiting
        ZonedDateTime staleHeartbeat = ZonedDateTime.now().minusMinutes(5);
        claimed.setLastHeartbeatAt(staleHeartbeat);
        lectureUnitProcessingStateRepository.save(claimed);

        PyrisWorkerHeartbeatResponseDTO response = request.postWithResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of(runId), List.of(runId)),
                PyrisWorkerHeartbeatResponseDTO.class, HttpStatus.OK, workerHeaders());

        assertThat(response).as("the heartbeat must answer with a body").isNotNull();
        assertThat(response.revokedRunIds()).as("a run id that belongs to an in-flight run must not be revoked").isNullOrEmpty();
        LectureUnitProcessingState renewed = reloadState();
        assertThat(renewed.getLastHeartbeatAt()).as("the heartbeat must renew the lease").isNotNull();
        assertThat(renewed.getLastHeartbeatAt().toInstant()).as("the lease must move forward").isAfter(staleHeartbeat.toInstant());
        assertThat(renewed.getIngestionJobToken()).as("a heartbeat must not change the run id").isEqualTo(runId);
        assertThat(renewed.getPhase()).as("a heartbeat must not change the phase").isEqualTo(claimed.getPhase());
    }

    @Test
    void heartbeatWithOnlyTheEarlierFieldStillRenewsTheLease() throws Exception {
        LectureUnitProcessingState claimed = claimSeededUnit();
        String runId = claimed.getIngestionJobToken();
        ZonedDateTime staleHeartbeat = ZonedDateTime.now().minusMinutes(5);
        claimed.setLastHeartbeatAt(staleHeartbeat);
        lectureUnitProcessingStateRepository.save(claimed);

        PyrisWorkerHeartbeatResponseDTO response = request.postWithResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, null, List.of(runId)),
                PyrisWorkerHeartbeatResponseDTO.class, HttpStatus.OK, workerHeaders());

        assertThat(response).isNotNull();
        assertThat(response.revokedJobTokens()).isNullOrEmpty();
        assertThat(reloadState().getLastHeartbeatAt().toInstant()).as("the lease must move forward").isAfter(staleHeartbeat.toInstant());
    }

    @Test
    void heartbeatWithUnknownRunIdReportsItRevoked() throws Exception {
        LectureUnitProcessingState claimed = claimSeededUnit();
        String knownRunId = claimed.getIngestionJobToken();
        String unknownRunId = UUID.randomUUID().toString();

        PyrisWorkerHeartbeatResponseDTO response = request.postWithResponseBody(HEARTBEAT_URL,
                new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of(knownRunId, unknownRunId), List.of(knownRunId, unknownRunId)), PyrisWorkerHeartbeatResponseDTO.class,
                HttpStatus.OK, workerHeaders());

        assertThat(response).as("the heartbeat must answer with a body").isNotNull();
        assertThat(response.revokedRunIds()).as("only the run Artemis does not track must be revoked").containsExactly(unknownRunId);
        assertThat(response.revokedJobTokens()).as("the earlier field carries the same list").containsExactly(unknownRunId);
    }

    /**
     * A run started before the upgrade to signed callback tokens keeps its old value in the processing state. Its callbacks can no longer authenticate, so the
     * heartbeat must stop keeping it alive: it is reported revoked, its lease is not renewed, the lease reaper queues the unit again, and the next claim hands
     * it out with a run id and a token of this version.
     */
    @Test
    void runFromBeforeTheUpgradeIsRevokedReclaimedAndHandedOutAgain() throws Exception {
        LectureUnitProcessingState claimed = claimSeededUnit();
        String preUpgradeValue = "artemis_example_org-node1-1760000000000-AbCdE12345";
        // Whole seconds, so the comparison below does not depend on the timestamp precision of the database.
        ZonedDateTime staleHeartbeat = ZonedDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
        claimed.setIngestionJobToken(preUpgradeValue);
        claimed.setLastHeartbeatAt(staleHeartbeat);
        lectureUnitProcessingStateRepository.save(claimed);

        PyrisWorkerHeartbeatResponseDTO response = request.postWithResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of(preUpgradeValue), null),
                PyrisWorkerHeartbeatResponseDTO.class, HttpStatus.OK, workerHeaders());

        assertThat(response).isNotNull();
        assertThat(response.revokedRunIds()).as("the value is echoed exactly as sent, so the worker finds its run").containsExactly(preUpgradeValue);
        LectureUnitProcessingState notRenewed = reloadState();
        assertThat(notRenewed.getLastHeartbeatAt().toInstant()).as("the lease of a pre-upgrade run must not be renewed").isEqualTo(staleHeartbeat.toInstant());

        // What the lease reaper does once the lease has lapsed
        boolean reclaimed = processingStateRecoveryService.reclaimLapsedLease(notRenewed.getId(), preUpgradeValue, List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING),
                ZonedDateTime.now().minusMinutes(1));
        assertThat(reclaimed).as("the lapsed lease must be reclaimed").isTrue();
        assertThat(reloadState().getIngestionJobToken()).as("the reclaimed unit no longer carries the old value").isNull();

        PyrisWorkerClaimResponseDTO reclaim = request.postWithResponseBody(CLAIM_URL, new PyrisWorkerClaimRequestDTO(BOOT_ID, 5), PyrisWorkerClaimResponseDTO.class, HttpStatus.OK,
                workerHeaders());
        assertThat(reclaim).isNotNull();
        assertThat(reclaim.jobs()).as("the unit is handed out again").hasSize(1);
        var settings = reclaim.jobs().getFirst().settings();
        assertThat(reloadState().getIngestionJobToken()).isEqualTo(settings.runId()).isNotEqualTo(preUpgradeValue);
        assertThat(callbackTokenService.verify(settings.authenticationToken()).map(PyrisCallbackTokenService.VerifiedCallback::runId)).contains(settings.runId());
    }

    @Test
    void claimWithWrongSecretIsForbidden() throws Exception {
        request.postWithoutResponseBody(CLAIM_URL, new PyrisWorkerClaimRequestDTO(BOOT_ID, 5), HttpStatus.FORBIDDEN, wrongSecretHeaders());

        LectureUnitProcessingState state = reloadState();
        assertThat(state.getPhase()).as("a rejected claim must not touch the queue").isEqualTo(ProcessingPhase.IDLE);
        assertThat(state.getIngestionJobToken()).as("a rejected claim must not register a job").isNull();
    }

    @Test
    void claimWithoutSecretIsForbidden() throws Exception {
        request.postWithoutResponseBody(CLAIM_URL, new PyrisWorkerClaimRequestDTO(BOOT_ID, 5), HttpStatus.FORBIDDEN, null);

        LectureUnitProcessingState state = reloadState();
        assertThat(state.getPhase()).as("a rejected claim must not touch the queue").isEqualTo(ProcessingPhase.IDLE);
        assertThat(state.getIngestionJobToken()).as("a rejected claim must not register a job").isNull();
    }

    @Test
    void heartbeatWithWrongSecretIsForbidden() throws Exception {
        request.postWithoutResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of("some-run"), null), HttpStatus.FORBIDDEN, wrongSecretHeaders());
    }

    @Test
    void heartbeatWithoutSecretIsForbidden() throws Exception {
        request.postWithoutResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of("some-run"), null), HttpStatus.FORBIDDEN, null);
    }
}
