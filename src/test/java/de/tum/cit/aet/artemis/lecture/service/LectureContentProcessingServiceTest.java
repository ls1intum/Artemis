package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.nebula.api.LectureTranscriptionApi;
import de.tum.cit.aet.artemis.nebula.api.TumLiveApi;

/**
 * Unit tests for {@link LectureContentProcessingService}.
 * Tests the automated lecture content processing pipeline including:
 * - Transcription triggering
 * - Ingestion triggering
 * - Cancellation (including Nebula job cancellation)
 * - State transitions
 * - Retry logic
 */
class LectureContentProcessingServiceTest {

    private static final String TEST_JOB_TOKEN = "test-ingestion-token-123";

    private LectureContentProcessingService service;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private LectureTranscriptionRepository transcriptionRepository;

    private LectureTranscriptionApi transcriptionApi;

    private TumLiveApi tumLiveApi;

    private IrisLectureApi irisLectureApi;

    private FeatureToggleService featureToggleService;

    private AttachmentVideoUnit testUnit;

    private Lecture testLecture;

    private LectureUnitProcessingState testState;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        transcriptionRepository = mock(LectureTranscriptionRepository.class);
        transcriptionApi = mock(LectureTranscriptionApi.class);
        tumLiveApi = mock(TumLiveApi.class);
        irisLectureApi = mock(IrisLectureApi.class);
        featureToggleService = mock(FeatureToggleService.class);

        // Enable the feature toggle by default
        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);

        service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.of(transcriptionApi), Optional.of(tumLiveApi),
                Optional.of(irisLectureApi), featureToggleService);

        // Set up test data
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

    // ==================== FLOW 1: New Unit with Video ====================

    @Nested
    class TriggerProcessingNewUnit {

        @Test
        void shouldCreateProcessingStateForNewUnit() {
            // Given: A new unit with video source, no existing state
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(testUnit.getVideoSource())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("job-123");

            // When
            service.triggerProcessing(testUnit);

            // Then: State should be saved when transitioning to TRANSCRIBING
            ArgumentCaptor<LectureUnitProcessingState> stateCaptor = ArgumentCaptor.forClass(LectureUnitProcessingState.class);
            verify(processingStateRepository).save(stateCaptor.capture());

            LectureUnitProcessingState savedState = stateCaptor.getValue();
            assertThat(savedState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
        }

        @Test
        void shouldStartTranscriptionWhenPlaylistFound() {
            // Given
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(testUnit.getVideoSource())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("job-123");

            // When
            service.triggerProcessing(testUnit);

            // Then
            verify(transcriptionApi).startNebulaTranscription(eq(testLecture.getId()), eq(testUnit.getId()), any(NebulaTranscriptionRequestDTO.class));
        }

        @Test
        void shouldSkipTranscriptionWhenNoPlaylistFound() {
            // Given
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(testUnit.getVideoSource())).thenReturn(Optional.empty());
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should skip transcription and go to ingestion
            verify(transcriptionApi, never()).startNebulaTranscription(anyLong(), anyLong(), any());
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
        }

        @Test
        void shouldGoDirectlyToIngestionForPdfOnlyUnit() {
            // Given: Unit with PDF but no video
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should skip transcription entirely
            verify(tumLiveApi, never()).getTumLivePlaylistLink(any());
            verify(transcriptionApi, never()).startNebulaTranscription(anyLong(), anyLong(), any());
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
        }

        @Test
        void shouldNotProcessUnitWithNoContent() {
            // Given: Unit with no video and no PDF
            testUnit.setVideoSource(null);
            testUnit.setAttachment(null);

            // When
            service.triggerProcessing(testUnit);

            // Then: Should not start processing
            verify(processingStateRepository, never()).save(any());
            verify(tumLiveApi, never()).getTumLivePlaylistLink(any());
        }

        @Test
        void shouldSkipTutorialLectures() {
            // Given
            testLecture.setIsTutorialLecture(true);

            // When
            service.triggerProcessing(testUnit);

            // Then
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldHandlePlaylistCheckExceptionGracefully() {
            // Given: Unit with video AND PDF, playlist check throws exception
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenThrow(new RuntimeException("TUM Live unavailable"));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should fall back to PDF-only ingestion
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
            verify(transcriptionApi, never()).startNebulaTranscription(anyLong(), anyLong(), any());
        }

        @Test
        void shouldStayIdleWhenPlaylistCheckFailsWithoutPdf() {
            // Given: Unit with video but NO PDF, playlist check throws exception
            testUnit.setAttachment(null);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenThrow(new RuntimeException("TUM Live unavailable"));

            // When
            service.triggerProcessing(testUnit);

            // Then: Should stay in IDLE (no processing possible)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        }

        @Test
        void shouldTransitionToIngestingWhenIngestionApiThrows() {
            // Given: PDF-only unit, ingestion API throws exception
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenThrow(new RuntimeException("Pyris unavailable"));

            // When
            service.triggerProcessing(testUnit);

            // Then: Should transition to INGESTING so scheduler can retry
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
            assertThat(testState.getRetryCount()).isEqualTo(1);
        }
    }

    // ==================== FLOW 2: Video URL Changed ====================

    @Nested
    class TriggerProcessingVideoChanged {

        @Test
        void shouldDetectVideoUrlChangeAndReprocess() {
            // Given: Existing state with different video hash
            testState.setVideoSourceHash("old-hash-12345");
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(testUnit.getVideoSource())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("new-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should restart processing
            verify(transcriptionApi).startNebulaTranscription(anyLong(), anyLong(), any());
        }

        @Test
        void shouldCancelOnNebulaWhenVideoChangedDuringTranscription() {
            // Given: Unit is currently transcribing, video URL changed
            testState.setVideoSourceHash("old-hash-12345");
            testState.setPhase(ProcessingPhase.TRANSCRIBING);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(testUnit.getVideoSource())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("new-job");
            doNothing().when(transcriptionApi).cancelNebulaTranscription(anyLong());

            // When
            service.triggerProcessing(testUnit);

            // Then: Should cancel on Nebula before restarting
            verify(transcriptionApi).cancelNebulaTranscription(testUnit.getId());
            verify(transcriptionApi).startNebulaTranscription(anyLong(), anyLong(), any());
        }

        @Test
        void shouldDeleteFromPyrisWhenVideoChanged() {
            // Given
            testState.setVideoSourceHash("old-hash-12345");
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(testUnit.getVideoSource())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("new-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should delete from Pyris
            verify(irisLectureApi).deleteLectureFromPyrisDB(any());
        }

        @Test
        void shouldNotReprocessIfContentUnchanged() {
            // Given: Same video hash, already done
            testUnit.setVideoSource("https://live.rbg.tum.de/w/course/12345");

            // Compute the same hash the service would compute
            testState.setVideoSourceHash(computeTestHash("https://live.rbg.tum.de/w/course/12345"));
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When
            service.triggerProcessing(testUnit);

            // Then: Should not restart processing
            verify(transcriptionApi, never()).startNebulaTranscription(anyLong(), anyLong(), any());
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

    // ==================== FLOW 3: Transcription Complete ====================

    @Nested
    class HandleTranscriptionComplete {

        @Test
        void shouldMoveToIngestionOnSuccess() {
            // Given
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            LectureTranscription transcription = new LectureTranscription();
            transcription.setId(42L);
            transcription.setLectureUnit(testUnit);
            transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(transcription));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.handleTranscriptionComplete(transcription);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
            verify(irisLectureApi).addLectureUnitToPyrisDB(any());
        }

        @Test
        void shouldResetRetryCountWhenMovingToIngestion() {
            // Given: Transcription succeeded after some retries
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(3); // Had 3 retries during transcription
            LectureTranscription transcription = new LectureTranscription();
            transcription.setId(42L);
            transcription.setLectureUnit(testUnit);
            transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(transcription));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.handleTranscriptionComplete(transcription);

            // Then: Retry count should be reset for fresh ingestion retries
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
            assertThat(testState.getRetryCount()).isZero();
        }

        @Test
        void shouldIncrementRetryCountAndSetRetryEligibleAtOnFailure() {
            // Given
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(0);
            LectureTranscription transcription = new LectureTranscription();
            transcription.setId(42L);
            transcription.setLectureUnit(testUnit);
            transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(transcription));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ZonedDateTime beforeCall = ZonedDateTime.now();

            // When
            service.handleTranscriptionComplete(transcription);

            // Then
            assertThat(testState.getRetryCount()).isEqualTo(1);
            assertThat(testState.getRetryEligibleAt()).isNotNull();
            // Backoff for retry 1 is 2^1 = 2 minutes
            ZonedDateTime expectedEligibleAt = beforeCall.plusMinutes(2);
            assertThat(testState.getRetryEligibleAt()).isAfterOrEqualTo(expectedEligibleAt.minusSeconds(5));
            assertThat(testState.getRetryEligibleAt()).isBeforeOrEqualTo(expectedEligibleAt.plusSeconds(5));
        }

        @Test
        void shouldMarkAsFailedAfterMaxRetries() {
            // Given: Already at max retries (MAX_PROCESSING_RETRIES = 5)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(4); // Will become 5 after this failure
            LectureTranscription transcription = new LectureTranscription();
            transcription.setId(42L);
            transcription.setLectureUnit(testUnit);
            transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(transcription));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.handleTranscriptionComplete(transcription);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        }

        @Test
        void shouldFallbackToPdfIngestionOnTranscriptionFailure() {
            // Given: Transcription failed but unit has PDF, at max retries (MAX_PROCESSING_RETRIES = 5)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(4); // Will become 5 after this failure, triggering fallback
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            testUnit.setAttachment(pdfAttachment);

            LectureTranscription transcription = new LectureTranscription();
            transcription.setId(42L);
            transcription.setLectureUnit(testUnit);
            transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(transcription));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.handleTranscriptionComplete(transcription);

            // Then: Should proceed with PDF-only ingestion after max retries
            verify(irisLectureApi).addLectureUnitToPyrisDB(any());
        }
    }

    // ==================== FLOW 5: Ingestion Complete ====================

    @Nested
    class HandleIngestionComplete {

        @Test
        void shouldMarkAsDoneOnSuccess() {
            // Given
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
            assertThat(testState.getIngestionJobToken()).isNull(); // Token cleared after success
        }

        @Test
        void shouldHandleCallbackWhenStateNotFound() {
            // Given: State was deleted (unit may have been removed during ingestion)
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());

            // When: Callback arrives for deleted unit
            service.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true);

            // Then: Should handle gracefully without exception
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldIgnoreStaleCallbackWhenNotIngesting() {
            // Given: State is in a different phase (callback may be stale)
            testState.setPhase(ProcessingPhase.DONE);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When: Stale callback arrives
            service.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true);

            // Then: Should ignore and not modify state
            verify(processingStateRepository, never()).save(any());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldIgnoreStaleCallbackWithWrongToken() {
            // Given: State has a different token (new job was started)
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken("new-job-token");
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When: Stale callback arrives with old token
            service.handleIngestionComplete(testUnit.getId(), "old-job-token", true);

            // Then: Should ignore - no save, state unchanged
            verify(processingStateRepository, never()).save(any());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        }

        @Test
        void shouldMarkAsFailedAfterMaxIngestionRetries() {
            // Given (MAX_PROCESSING_RETRIES = 5)
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            testState.setRetryCount(4); // Will become 5 after failure
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        }
    }

    // ==================== FLOW 6: Retry Processing ====================

    @Nested
    class RetryProcessing {

        @Test
        void shouldRetryOnlyIfFailed() {
            // Given: Failed state with retry count from previous attempts
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setRetryCount(5);
            testState.setId(42L); // Existing state in DB

            // First call finds FAILED state, second call (after delete) returns empty
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState)).thenReturn(Optional.empty());
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("new-job");

            // When
            service.retryProcessing(testUnit);

            // Then: Old state deleted, fresh state created and processing started
            verify(processingStateRepository).delete(testState);
            verify(transcriptionApi).startNebulaTranscription(anyLong(), anyLong(), any());
        }

        @Test
        void shouldNotRetryIfNotFailed() {
            // Given
            testState.setPhase(ProcessingPhase.DONE);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When
            service.retryProcessing(testUnit);

            // Then: Should not restart
            verify(transcriptionApi, never()).startNebulaTranscription(anyLong(), anyLong(), any());
        }
    }

    // ==================== Exponential Backoff ====================

    @Nested
    class ExponentialBackoff {

        @Test
        void shouldCalculateCorrectBackoffMinutes() {
            // Exponential backoff formula: 2^retryCount minutes
            assertThat(LectureContentProcessingService.calculateBackoffMinutes(1)).isEqualTo(2);
            assertThat(LectureContentProcessingService.calculateBackoffMinutes(2)).isEqualTo(4);
            assertThat(LectureContentProcessingService.calculateBackoffMinutes(3)).isEqualTo(8);
            assertThat(LectureContentProcessingService.calculateBackoffMinutes(4)).isEqualTo(16);
            assertThat(LectureContentProcessingService.calculateBackoffMinutes(5)).isEqualTo(32);
        }

        @Test
        void shouldStayInTranscribingPhaseOnFailureForSchedulerRetry() {
            // Given: Transcription fails but not at max retries
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(0);
            LectureTranscription transcription = new LectureTranscription();
            transcription.setId(42L);
            transcription.setLectureUnit(testUnit);
            transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(transcriptionRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(transcription));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.handleTranscriptionComplete(transcription);

            // Then: Should stay in TRANSCRIBING phase (scheduler will retry with backoff)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
            assertThat(testState.getRetryCount()).isEqualTo(1);
            // Should NOT trigger immediate retry - no transcription API call
            verify(transcriptionApi, never()).startNebulaTranscription(anyLong(), anyLong(), any());
        }

        @Test
        void shouldStayInIngestingPhaseOnFailureForSchedulerRetry() {
            // Given: Ingestion fails but not at max retries
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            testState.setRetryCount(1);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false);

            // Then: Should stay in INGESTING phase (scheduler will retry with backoff)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
            assertThat(testState.getRetryCount()).isEqualTo(2);
            // Should NOT trigger immediate retry - no ingestion API call
            verify(irisLectureApi, never()).addLectureUnitToPyrisDB(any());
        }

        @Test
        void shouldRetryTranscriptionWhenCalledByScheduler() {
            // Given: State is ready for scheduler retry
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(2);
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("retry-job");

            // When: Scheduler calls retryTranscription
            service.retryTranscription(testState);

            // Then: Should start new transcription job
            verify(transcriptionApi).startNebulaTranscription(anyLong(), anyLong(), any());
        }

        @Test
        void shouldRetryIngestionWhenCalledByScheduler() {
            // Given: State is ready for scheduler retry
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setRetryCount(3);
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("retry-ingestion");

            // When: Scheduler calls retryIngestion
            service.retryIngestion(testState);

            // Then: Should start new ingestion job
            verify(irisLectureApi).addLectureUnitToPyrisDB(any());
        }

        @Test
        void shouldClearRetryEligibleAtWhenRetryStarts() {
            // Given: State has retryEligibleAt set (was waiting for retry)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1);
            testState.scheduleRetry(2); // Sets retryEligibleAt
            assertThat(testState.getRetryEligibleAt()).isNotNull(); // Precondition

            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("retry-job");

            // When: Scheduler calls retryTranscription
            service.retryTranscription(testState);

            // Then: retryEligibleAt should be cleared after successful retry start
            assertThat(testState.getRetryEligibleAt()).isNull();
        }
    }

    // ==================== Service Not Available Scenarios ====================

    @Nested
    class ServiceNotAvailable {

        @Test
        void shouldSkipTranscriptionWhenNebulaNotAvailable() {
            // Given: Service created without transcription API
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.empty(), // No transcription API
                    Optional.of(tumLiveApi), Optional.of(irisLectureApi), featureToggleService);

            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should go to ingestion without transcription
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
        }

        @Test
        void shouldSkipPlaylistCheckWhenTumLiveNotAvailable() {
            // Given: Service created without TUM Live API
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.of(transcriptionApi), Optional.empty(), // No TUM Live API
                    Optional.of(irisLectureApi), featureToggleService);

            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should go to ingestion without checking playlist
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
        }

        @Test
        void shouldStillTranscribeWhenIrisNotAvailable() {
            // Given: Service created WITHOUT Iris API but WITH Nebula (transcription only deployment)
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.of(transcriptionApi), Optional.of(tumLiveApi),
                    Optional.empty(), featureToggleService); // No Iris API

            // Unit has video (can transcribe) - transcription should still happen
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("job-123");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should transcribe (transcriptions are useful even without Iris)
            verify(transcriptionApi).startNebulaTranscription(anyLong(), anyLong(), any());
        }

        @Test
        void shouldSkipProcessingWhenNeitherNebulaNorIrisAvailable() {
            // Given: Service with NO Iris and NO Nebula (no processing possible)
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.empty(), // No Nebula
                    Optional.of(tumLiveApi), Optional.empty(), featureToggleService); // No Iris

            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            // When
            service.triggerProcessing(testUnit);

            // Then: Should skip entirely - no state created, no APIs called
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldSkipPdfOnlyUnitWhenIrisNotAvailable() {
            // Given: Service WITHOUT Iris, unit has only PDF (no video)
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.of(transcriptionApi), Optional.of(tumLiveApi),
                    Optional.empty(), featureToggleService); // No Iris API

            testUnit.setVideoSource(null); // No video
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            // When
            service.triggerProcessing(testUnit);

            // Then: Should skip - PDF-only needs Iris for ingestion
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldGoToIdleWhenNebulaUnavailableAndNoPdf() {
            // Given: Service without Nebula, unit has video but no PDF
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.empty(), // No transcription API
                    Optional.of(tumLiveApi), Optional.of(irisLectureApi), featureToggleService);

            testUnit.setAttachment(null); // No PDF

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://playlist.m3u8"));

            // When
            service.triggerProcessing(testUnit);

            // Then: Should go to IDLE (not FAILED) since Nebula is intentionally unavailable
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(testState.getErrorKey()).isNull(); // Not an error, just nothing to do
        }
    }
}
