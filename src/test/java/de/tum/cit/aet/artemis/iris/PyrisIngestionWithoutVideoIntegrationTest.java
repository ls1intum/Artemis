package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimResponseDTO;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateReconcileRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitContentFingerprintService;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.service.TumLivePlaylistLookup;

/**
 * The persisted path of a unit whose TUM Live video cannot be resolved, through the real worker claim: the fingerprint the payload carries is the one the
 * claim activates with and the one the completion confirms, and a later run with the resolved video confirms the full fingerprint.
 */
class PyrisIngestionWithoutVideoIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String CLAIM_URL = "/api/iris/internal/ingestion/worker/claim";

    private static final String BOOT_ID = "pyrisingestionwithoutvideo-boot";

    private static final String TUM_LIVE_VIDEO = "https://live.rbg.tum.de/w/course/4711";

    @Value("${artemis.iris.secret-token}")
    private String pyrisSecretToken;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureUnitProcessingStateRepository processingStateRepository;

    @Autowired
    private LectureUnitProcessingStateReconcileRepository reconcileStateRepository;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitTestRepository;

    @Autowired
    private LectureUnitContentFingerprintService contentFingerprintService;

    @Autowired
    private ProcessingStateCallbackApi processingStateCallbackApi;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Lecture lecture;

    @BeforeEach
    void initTestCase() {
        featureToggleService.enableFeature(Feature.LectureContentProcessing);
        // The table is shared with the rest of the suite; an IDLE row of another class would be claimed as well
        processingStateRepository.deleteAll();
        lecture = lectureUtilService.createCourseWithLecture(true);
        when(tumLiveService.isTumLiveUrl(TUM_LIVE_VIDEO)).thenReturn(true);
    }

    private AttachmentVideoUnit queuedUnit(boolean withPdf) {
        AttachmentVideoUnit unit = withPdf ? lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 0, true)
                : lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        unit.setVideoSource(TUM_LIVE_VIDEO);
        unit = attachmentVideoUnitTestRepository.save(unit);
        processingStateRepository.save(new LectureUnitProcessingState(unit));
        return unit;
    }

    private PyrisWorkerClaimResponseDTO claim() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, pyrisSecretToken);
        return request.postWithResponseBody(CLAIM_URL, new PyrisWorkerClaimRequestDTO(BOOT_ID, 5), PyrisWorkerClaimResponseDTO.class, HttpStatus.OK, headers);
    }

    private LectureUnitProcessingState stateOf(AttachmentVideoUnit unit) {
        return processingStateRepository.findByLectureUnit_Id(unit.getId()).orElseThrow();
    }

    @Test
    void unitWithSlidesIsIngestedWithoutTheVideoAndLaterWithIt() throws Exception {
        AttachmentVideoUnit unit = queuedUnit(true);
        LectureUnitContentFingerprintService.ContentFingerprints fingerprints = contentFingerprintService.computeFingerprints(unit);
        when(tumLiveService.lookUpPlaylist(anyString())).thenReturn(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_ACCESSIBLE));

        PyrisWorkerClaimResponseDTO response = claim();

        assertThat(response.jobs()).hasSize(1);
        PyrisLectureUnitWebhookDTO sent = response.jobs().getFirst().pyrisLectureUnit();
        assertThat(sent.videoLink()).as("the video that cannot be resolved is left out").isNull();
        assertThat(sent.pdfFile()).as("the slides are sent").isNotEmpty();
        assertThat(sent.contentFingerprint()).isEqualTo(fingerprints.withoutVideo());
        LectureUnitProcessingState activated = stateOf(unit);
        assertThat(activated.getPhase()).as("nothing to transcribe").isEqualTo(ProcessingPhase.INGESTING);
        assertThat(activated.getContentFingerprint()).as("activated with what was sent").isEqualTo(fingerprints.withoutVideo());

        processingStateCallbackApi.handleIngestionComplete(unit.getId(), activated.getIngestionJobToken(), true, null, null);
        LectureUnitProcessingState done = stateOf(unit);
        assertThat(done.getPhase()).isEqualTo(ProcessingPhase.DONE);
        assertThat(done.getConfirmedFingerprint()).as("confirmed with the stamp Iris writes").isEqualTo(fingerprints.withoutVideo());

        // The recording is there now; the reconcile's video recheck queues the unit again, and this run confirms the full content
        when(tumLiveService.lookUpPlaylist(anyString())).thenReturn(new TumLivePlaylistLookup("https://live.rbg.tum.de/playlist.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED));
        assertThat(reconcileStateRepository.requeueForReconcileIfUnchanged(done.getId(), ProcessingPhase.DONE, fingerprints.withoutVideo(), fingerprints.withoutVideo(), null, null,
                2, ZonedDateTime.now())).isEqualTo(1);

        PyrisLectureUnitWebhookDTO resent = claim().jobs().getFirst().pyrisLectureUnit();
        assertThat(resent.videoLink()).isEqualTo("https://live.rbg.tum.de/playlist.m3u8");
        assertThat(resent.contentFingerprint()).isEqualTo(fingerprints.full());
        LectureUnitProcessingState rerun = stateOf(unit);
        processingStateCallbackApi.handleIngestionComplete(unit.getId(), rerun.getIngestionJobToken(), true, null, null);
        assertThat(stateOf(unit).getConfirmedFingerprint()).isEqualTo(fingerprints.full());
    }

    @Test
    void unitWithOnlyTheVideoFailsWithARetryableVideoError() throws Exception {
        AttachmentVideoUnit unit = queuedUnit(false);
        when(tumLiveService.lookUpPlaylist(anyString())).thenReturn(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_AVAILABLE));

        assertThat(claim().jobs()).as("nothing to hand to the worker").isNullOrEmpty();

        LectureUnitProcessingState failed = stateOf(unit);
        assertThat(failed.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        assertThat(failed.getErrorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.videoSourceUnavailable");
        assertThat(failed.getRetryEligibleAt()).as("retried later").isNotNull();
        assertThat(failed.getClaimToken()).as("settled, so its wait holds no backlog room").isNull();
    }
}
