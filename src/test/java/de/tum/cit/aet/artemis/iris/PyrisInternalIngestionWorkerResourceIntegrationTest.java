package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimResponseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerHeartbeatResponseDTO;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
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
    void heartbeatWithClaimedJobTokenRenewsTheLease() throws Exception {
        LectureUnitProcessingState claimed = claimSeededUnit();
        String jobToken = claimed.getIngestionJobToken();

        // Age the lease so that the renewal is observable without waiting
        ZonedDateTime staleHeartbeat = ZonedDateTime.now().minusMinutes(5);
        claimed.setLastHeartbeatAt(staleHeartbeat);
        lectureUnitProcessingStateRepository.save(claimed);

        PyrisWorkerHeartbeatResponseDTO response = request.postWithResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of(jobToken)),
                PyrisWorkerHeartbeatResponseDTO.class, HttpStatus.OK, workerHeaders());

        assertThat(response).as("the heartbeat must answer with a body").isNotNull();
        assertThat(response.revokedJobTokens()).as("a token that belongs to an in-flight run must not be revoked").isNullOrEmpty();
        LectureUnitProcessingState renewed = reloadState();
        assertThat(renewed.getLastHeartbeatAt()).as("the heartbeat must renew the lease").isNotNull();
        assertThat(renewed.getLastHeartbeatAt().toInstant()).as("the lease must move forward").isAfter(staleHeartbeat.toInstant());
        assertThat(renewed.getIngestionJobToken()).as("a heartbeat must not change the job token").isEqualTo(jobToken);
        assertThat(renewed.getPhase()).as("a heartbeat must not change the phase").isEqualTo(claimed.getPhase());
    }

    @Test
    void heartbeatWithUnknownJobTokenReportsItRevoked() throws Exception {
        LectureUnitProcessingState claimed = claimSeededUnit();
        String knownToken = claimed.getIngestionJobToken();
        String unknownToken = TEST_PREFIX + "-unknown-token";

        PyrisWorkerHeartbeatResponseDTO response = request.postWithResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of(knownToken, unknownToken)),
                PyrisWorkerHeartbeatResponseDTO.class, HttpStatus.OK, workerHeaders());

        assertThat(response).as("the heartbeat must answer with a body").isNotNull();
        assertThat(response.revokedJobTokens()).as("only the token Artemis does not track must be revoked").containsExactly(unknownToken);
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
        request.postWithoutResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of("some-token")), HttpStatus.FORBIDDEN, wrongSecretHeaders());
    }

    @Test
    void heartbeatWithoutSecretIsForbidden() throws Exception {
        request.postWithoutResponseBody(HEARTBEAT_URL, new PyrisWorkerHeartbeatRequestDTO(BOOT_ID, List.of("some-token")), HttpStatus.FORBIDDEN, null);
    }
}
