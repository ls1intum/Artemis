package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Unit tests for {@link LectureContentProcessingService} and {@link ProcessingStateCallbackService}.
 * Tests the unified Iris-based processing pipeline including:
 * - Enqueuing units as IDLE
 * - Capacity-aware dispatch
 * - Checkpoint handling (transcription data)
 * - Completion callbacks
 * - Failure and retry logic
 */
class LectureContentProcessingServiceTest {

    private static final String TEST_JOB_TOKEN = "test-ingestion-token-123";

    private LectureContentProcessingService service;

    private ProcessingStateCallbackService callbackService;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private LectureTranscriptionRepository transcriptionRepository;

    private IrisLectureApi irisLectureApi;

    private WebsocketMessagingService websocketMessagingService;

    private AttachmentVideoUnit testUnit;

    private Lecture testLecture;

    private LectureUnitProcessingState testState;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        transcriptionRepository = mock(LectureTranscriptionRepository.class);
        irisLectureApi = mock(IrisLectureApi.class);
        FeatureToggleService featureToggleService = mock(FeatureToggleService.class);

        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);

        websocketMessagingService = mock(WebsocketMessagingService.class);
        callbackService = new ProcessingStateCallbackService(processingStateRepository, transcriptionRepository, Optional.of(irisLectureApi), websocketMessagingService);

        service = new LectureContentProcessingService(processingStateRepository, Optional.of(irisLectureApi), featureToggleService, callbackService);

        testLecture = new Lecture();
        testLecture.setId(1L);
        testLecture.setTitle("Test Lecture");

        testUnit = new AttachmentVideoUnit();
        testUnit.setId(100L);
        testUnit.setLecture(testLecture);
        testUnit.setVideoSource("https://live.rbg.tum.de/w/course/12345");

        testState = new LectureUnitProcessingState(testUnit);
        testState.setPhase(ProcessingPhase.IDLE);
    }

    // ==================== FLOW 1: Enqueue New Unit ====================

    @Nested
    class TriggerProcessingNewUnit {

        @Test
        void shouldEnqueueUnitAsIdle() {
            // Given: A new unit with video source
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // Mock dispatch — no slots available (dispatch tested separately)
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            // When
            service.triggerProcessing(testUnit);

            // Then: State should be saved as IDLE
            ArgumentCaptor<LectureUnitProcessingState> stateCaptor = ArgumentCaptor.forClass(LectureUnitProcessingState.class);
            verify(processingStateRepository).save(stateCaptor.capture());

            LectureUnitProcessingState savedState = stateCaptor.getValue();
            assertThat(savedState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        }

        @Test
        void shouldNotProcessUnitWithNoContent() {
            testUnit.setVideoSource(null);
            testUnit.setAttachment(null);

            service.triggerProcessing(testUnit);

            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldSkipTutorialLectures() {
            testLecture.setIsTutorialLecture(true);

            service.triggerProcessing(testUnit);

            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldSkipWhenIrisNotAvailable() {
            // Given: No Iris API
            FeatureToggleService fts = mock(FeatureToggleService.class);
            when(fts.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);
            ProcessingStateCallbackService noIrisCallback = new ProcessingStateCallbackService(processingStateRepository, transcriptionRepository, Optional.empty(),
                    mock(WebsocketMessagingService.class));
            service = new LectureContentProcessingService(processingStateRepository, Optional.empty(), fts, noIrisCallback);

            service.triggerProcessing(testUnit);

            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldNotReenqueueAlreadyProcessingUnit() {
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            service.triggerProcessing(testUnit);

            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldNotReenqueueDoneUnit() {
            testState.setPhase(ProcessingPhase.DONE);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            service.triggerProcessing(testUnit);

            verify(processingStateRepository, never()).save(any());
        }
    }

    // ==================== FLOW 2: Capacity-Aware Dispatch ====================

    @Nested
    class DispatchPendingJobs {

        @Test
        void shouldDispatchIdleJobWithVideoAsTranscribing() {
            // Given: One IDLE job, one slot available, unit has video, no existing transcription
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(1L); // 1 slot available (MAX_CONCURRENT_PROCESSING - 1)
            when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn(TEST_JOB_TOKEN);
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            callbackService.dispatchPendingJobs();

            // Then: Should dispatch as TRANSCRIBING (has video, no completed transcription)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
            assertThat(testState.getIngestionJobToken()).isEqualTo(TEST_JOB_TOKEN);
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
        }

        @Test
        void shouldDispatchAsIngestingWhenTranscriptionExists() {
            // Given: IDLE job with completed transcription (retry after ingestion failure)
            LectureTranscription completedTranscription = new LectureTranscription();
            completedTranscription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

            when(processingStateRepository.countByPhaseIn(any())).thenReturn(0L);
            when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(completedTranscription));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn(TEST_JOB_TOKEN);
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            callbackService.dispatchPendingJobs();

            // Then: Should dispatch as INGESTING (transcription already done)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        }

        @Test
        void shouldDispatchPdfOnlyAsIngesting() {
            // Given: IDLE job with no video (PDF only)
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.countByPhaseIn(any())).thenReturn(0L);
            when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn(TEST_JOB_TOKEN);
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            callbackService.dispatchPendingJobs();

            // Then: Should dispatch as INGESTING (no video to transcribe)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        }

        @Test
        void shouldNotDispatchWhenNoSlotsAvailable() {
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L); // All slots full

            callbackService.dispatchPendingJobs();

            verify(processingStateRepository, never()).findIdleForDispatch(any(), anyInt());
        }

        @Test
        void shouldMarkDoneWhenIrisReturnsNull() {
            // Given: Iris returns null (not applicable for course)
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(0L);
            when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn(null);
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            callbackService.dispatchPendingJobs();

            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldHandleDispatchFailureWithRetry() {
            // Given: Iris throws exception during dispatch
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(0L);
            when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenThrow(new RuntimeException("Iris unavailable"));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            callbackService.dispatchPendingJobs();

            // Then: Should mark as FAILED with retry backoff scheduled
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            assertThat(testState.getRetryCount()).isEqualTo(1);
            assertThat(testState.getRetryEligibleAt()).isNotNull();
            assertThat(testState.getStartedAt()).isNull(); // Back in queue
        }
    }

    // ==================== FLOW 3: Checkpoint Handling ====================

    @Nested
    class HandleCheckpointData {

        @Test
        void shouldSaveRawTranscriptionAndStayInTranscribing() {
            // Given: State is TRANSCRIBING with valid token
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());
            when(transcriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Raw transcript: all slideNumber=0
            String rawJson = "{\"language\":\"en\",\"segments\":[{\"startTime\":0.0,\"endTime\":5.0,\"text\":\"Hello\",\"slideNumber\":0}]}";

            // When
            callbackService.handleCheckpointData(testUnit.getId(), TEST_JOB_TOKEN, rawJson);

            // Then: Should save transcription as PENDING and stay in TRANSCRIBING
            verify(transcriptionRepository).save(any());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
        }

        @Test
        void shouldSaveEnrichedTranscriptionAndTransitionToIngesting() {
            // Given: State is TRANSCRIBING with valid token
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            testState.setRetryCount(2); // Had retries during transcription

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());
            when(transcriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Enriched transcript: some slideNumber≠0
            String enrichedJson = "{\"language\":\"en\",\"segments\":[{\"startTime\":0.0,\"endTime\":5.0,\"text\":\"Hello\",\"slideNumber\":1}]}";

            // When
            callbackService.handleCheckpointData(testUnit.getId(), TEST_JOB_TOKEN, enrichedJson);

            // Then: Should save as COMPLETED and transition to INGESTING
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
            assertThat(testState.getRetryCount()).isZero(); // Reset for ingestion phase
        }

        @Test
        void shouldIgnoreCheckpointWithStaleToken() {
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setIngestionJobToken("current-token");

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            callbackService.handleCheckpointData(testUnit.getId(), "stale-token", "{\"segments\":[]}");

            verify(transcriptionRepository, never()).save(any());
        }

        @Test
        void shouldIgnoreCheckpointWhenNotTranscribing() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            callbackService.handleCheckpointData(testUnit.getId(), TEST_JOB_TOKEN, "{\"segments\":[{\"startTime\":0,\"endTime\":5,\"text\":\"x\",\"slideNumber\":0}]}");

            verify(transcriptionRepository, never()).save(any());
        }

        @Test
        void shouldIgnoreNullOrBlankResult() {
            callbackService.handleCheckpointData(testUnit.getId(), TEST_JOB_TOKEN, null);
            callbackService.handleCheckpointData(testUnit.getId(), TEST_JOB_TOKEN, "");
            callbackService.handleCheckpointData(testUnit.getId(), TEST_JOB_TOKEN, "  ");

            verify(processingStateRepository, never()).findByLectureUnit_Id(anyLong());
        }
    }

    // ==================== FLOW 4: Completion Callbacks ====================

    @Nested
    class HandleIngestionComplete {

        @Test
        void shouldMarkAsDoneOnSuccess() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // Mock dispatch after completion
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            callbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true, null);

            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
            assertThat(testState.getIngestionJobToken()).isNull();
        }

        @Test
        void shouldAlsoAcceptSuccessFromTranscribingPhase() {
            // Iris may complete the entire pipeline (transcription + ingestion) while
            // Artemis still shows TRANSCRIBING (no checkpoint sent)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            callbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true, null);

            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldMarkAsFailedWithRetryOnFailure() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            testState.setRetryCount(0);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            callbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false, null);

            // Should mark as FAILED with backoff scheduled for re-dispatch
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            assertThat(testState.getRetryCount()).isEqualTo(1);
            assertThat(testState.getStartedAt()).isNull();
            assertThat(testState.getRetryEligibleAt()).isNotNull();
        }

        @Test
        void shouldMarkAsFailedAfterMaxRetries() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            testState.setRetryCount(MAX_PROCESSING_RETRIES - 1);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            callbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false, null);

            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        }

        @Test
        void shouldIgnoreStaleCallbackWithWrongToken() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken("new-job-token");
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            callbackService.handleIngestionComplete(testUnit.getId(), "old-job-token", true, null);

            verify(processingStateRepository, never()).save(any());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        }

        @Test
        void shouldIgnoreCallbackWhenNotProcessing() {
            testState.setPhase(ProcessingPhase.DONE);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            callbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true, null);

            verify(processingStateRepository, never()).save(any());
        }
    }

    // ==================== FLOW 5: Retry Processing ====================

    @Nested
    class RetryProcessing {

        @Test
        void shouldRetryOnlyIfFailed() {
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setRetryCount(5);
            testState.setId(42L);

            // First call finds FAILED state, second call (after delete) returns empty, then returns new saved state
            AtomicReference<LectureUnitProcessingState> savedState = new AtomicReference<>();
            when(processingStateRepository.save(any(LectureUnitProcessingState.class))).thenAnswer(inv -> {
                savedState.set(inv.getArgument(0));
                return inv.getArgument(0);
            });
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState)).thenAnswer(inv -> Optional.ofNullable(savedState.get()));
            // Mock dispatch — no slots
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            verify(processingStateRepository).delete(testState);
            assertThat(result).isNotNull();
            assertThat(result.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        }

        @Test
        void shouldNotRetryIfNotFailed() {
            testState.setPhase(ProcessingPhase.DONE);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullWhenNoStateExists() {
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());

            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForNullInput() {
            assertThat(service.retryProcessing(null)).isNull();
        }
    }

    // ==================== FLOW 6: Content Change Detection ====================

    @Nested
    class ContentChangeDetection {

        @Test
        void shouldResetToIdleOnVideoUrlChange() {
            // Given: Existing DONE state with different video hash
            testState.setVideoSourceHash("old-hash-12345");
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // Mock dispatch — no slots
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            service.triggerProcessing(testUnit);

            // Then: Should reset to IDLE for re-processing
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(testState.getStartedAt()).isNull(); // In queue
            verify(processingStateRepository).save(testState);
        }

        @Test
        void shouldDeleteFromIrisOnVideoChange() {
            testState.setVideoSourceHash("old-hash-12345");
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            service.triggerProcessing(testUnit);

            verify(irisLectureApi).deleteLectureFromPyrisDB(any());
        }

        @Test
        void shouldNotReprocessIfContentUnchanged() {
            testUnit.setVideoSource("https://live.rbg.tum.de/w/course/12345");
            testState.setVideoSourceHash(computeTestHash("https://live.rbg.tum.de/w/course/12345"));
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            service.triggerProcessing(testUnit);

            // Should not re-enqueue — already done with same content
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        private String computeTestHash(String value) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return java.util.HexFormat.of().formatHex(hash);
            }
            catch (Exception e) {
                return String.valueOf(value.hashCode());
            }
        }
    }

    // ==================== Exponential Backoff ====================

    @Nested
    class ExponentialBackoff {

        @Test
        void shouldCalculateCorrectBackoffMinutes() {
            assertThat(ProcessingStateCallbackService.calculateBackoffMinutes(1)).isEqualTo(2);
            assertThat(ProcessingStateCallbackService.calculateBackoffMinutes(2)).isEqualTo(4);
            assertThat(ProcessingStateCallbackService.calculateBackoffMinutes(3)).isEqualTo(8);
            assertThat(ProcessingStateCallbackService.calculateBackoffMinutes(4)).isEqualTo(16);
            assertThat(ProcessingStateCallbackService.calculateBackoffMinutes(5)).isEqualTo(32);
        }

        @Test
        void shouldMarkAsFailedWithCorrectBackoff() {
            // Given: Ingestion fails (not at max retries)
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            testState.setRetryCount(1);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            ZonedDateTime beforeCall = ZonedDateTime.now();

            callbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false, null);

            // Then: Should mark as FAILED with backoff scheduled (2^2 = 4 minutes)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            assertThat(testState.getRetryCount()).isEqualTo(2);
            assertThat(testState.getRetryEligibleAt()).isNotNull();
            ZonedDateTime expectedEligibleAt = beforeCall.plusMinutes(4);
            assertThat(testState.getRetryEligibleAt()).isAfterOrEqualTo(expectedEligibleAt.minusSeconds(5));
            assertThat(testState.getRetryEligibleAt()).isBeforeOrEqualTo(expectedEligibleAt.plusSeconds(5));
        }
    }

    // ==================== WebSocket Notification Correctness ====================

    @Nested
    class WebSocketNotifications {

        @Test
        void shouldPreserveTranscriptionStatusOnFailure() {
            // Given: Unit in INGESTING phase with a completed transcription
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);

            LectureTranscription completedTranscription = new LectureTranscription();
            completedTranscription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(completedTranscription));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(10L);

            // When: Ingestion fails
            callbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false, null);

            // Then: WebSocket notification must include the transcription status (not null)
            ArgumentCaptor<LectureUnitCombinedStatusDTO> dtoCaptor = ArgumentCaptor.forClass(LectureUnitCombinedStatusDTO.class);
            verify(websocketMessagingService).sendMessage(anyString(), dtoCaptor.capture());

            LectureUnitCombinedStatusDTO sentDto = dtoCaptor.getValue();
            assertThat(sentDto.transcriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
            assertThat(sentDto.processingPhase()).isEqualTo(ProcessingPhase.FAILED);
        }
    }

    // ==================== Dispatch Slot Limiting ====================

    @Nested
    class DispatchSlotLimiting {

        @Test
        void shouldPassAvailableSlotsAsLimitToRetryQuery() {
            // Given: 1 active job, so 1 slot available (MAX_CONCURRENT_PROCESSING = 2)
            when(processingStateRepository.countByPhaseIn(any())).thenReturn(1L);
            when(processingStateRepository.findStatesReadyForRetry(anyString(), any(), anyInt())).thenReturn(List.of());
            when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of());

            // When
            callbackService.dispatchPendingJobs();

            // Then: Should pass availableSlots (2 - 1 = 1) as the limit
            verify(processingStateRepository).findStatesReadyForRetry(eq(ProcessingPhase.FAILED.name()), any(ZonedDateTime.class), eq(1));
        }
    }

    // ==================== Iris Reset ====================

    @Nested
    class IrisReset {

        @Test
        void shouldResetActiveStatesToIdlePreservingRetryBudget() {
            // Given: Two in-flight jobs with existing retry counts
            LectureUnitProcessingState transcribingState = new LectureUnitProcessingState(testUnit);
            transcribingState.setPhase(ProcessingPhase.TRANSCRIBING);
            transcribingState.setRetryCount(2);
            transcribingState.setIngestionJobToken("token-1");
            transcribingState.setStartedAt(ZonedDateTime.now().minusMinutes(5));

            AttachmentVideoUnit unit2 = new AttachmentVideoUnit();
            unit2.setId(200L);
            unit2.setLecture(testLecture);
            LectureUnitProcessingState ingestingState = new LectureUnitProcessingState(unit2);
            ingestingState.setPhase(ProcessingPhase.INGESTING);
            ingestingState.setRetryCount(3);
            ingestingState.setIngestionJobToken("token-2");
            ingestingState.setStartedAt(ZonedDateTime.now().minusMinutes(10));

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(transcribingState, ingestingState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transcriptionRepository.findByLectureUnit_Id(anyLong())).thenReturn(Optional.empty());

            // When
            int resetCount = callbackService.handleIrisReset();

            // Then: Both reset to IDLE, retry budget preserved, tokens cleared
            assertThat(resetCount).isEqualTo(2);

            assertThat(transcribingState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(transcribingState.getRetryCount()).isEqualTo(2); // Unchanged
            assertThat(transcribingState.getIngestionJobToken()).isNull();
            assertThat(transcribingState.getStartedAt()).isNull();

            assertThat(ingestingState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(ingestingState.getRetryCount()).isEqualTo(3); // Unchanged
            assertThat(ingestingState.getIngestionJobToken()).isNull();
            assertThat(ingestingState.getStartedAt()).isNull();

            verify(processingStateRepository, times(2)).save(any());
            verify(websocketMessagingService, times(2)).sendMessage(anyString(), any(LectureUnitCombinedStatusDTO.class));
        }
    }
}
