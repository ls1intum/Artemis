package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    private LectureContentProcessingService service;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private LectureTranscriptionRepository transcriptionRepository;

    private LectureTranscriptionApi transcriptionApi;

    private TumLiveApi tumLiveApi;

    private IrisLectureApi irisLectureApi;

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

        service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.of(transcriptionApi), Optional.of(tumLiveApi),
                Optional.of(irisLectureApi));

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
            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.empty());
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(testUnit.getVideoSource())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("job-123");

            // When
            service.triggerProcessing(testUnit);

            // Then: State should be created and saved
            ArgumentCaptor<LectureUnitProcessingState> stateCaptor = ArgumentCaptor.forClass(LectureUnitProcessingState.class);
            verify(processingStateRepository, times(4)).save(stateCaptor.capture()); // create, checking_playlist, playlist_url, transcribing

            LectureUnitProcessingState savedState = stateCaptor.getAllValues().get(3);
            assertThat(savedState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
        }

        @Test
        void shouldStartTranscriptionWhenPlaylistFound() {
            // Given
            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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
        void shouldSetErrorMessageWhenPlaylistCheckFailsWithoutPdf() {
            // Given: Unit with video but NO PDF, playlist check throws exception
            testUnit.setAttachment(null);

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenThrow(new RuntimeException("TUM Live unavailable"));

            // When
            service.triggerProcessing(testUnit);

            // Then: Should set error message and return to IDLE
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(testState.getErrorMessage()).contains("Playlist check failed");
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

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));

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

    // ==================== FLOW 3: Cancellation ====================

    @Nested
    class CancelProcessing {

        @Test
        void shouldCancelOnNebulaWhenTranscribing() {
            // Given: Unit is currently transcribing
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(transcriptionApi).cancelNebulaTranscription(anyLong());

            // When
            service.cancelProcessing(testUnit.getId());

            // Then: Should call Nebula cancel
            verify(transcriptionApi).cancelNebulaTranscription(testUnit.getId());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(testState.getErrorMessage()).isEqualTo("Cancelled");
        }

        @Test
        void shouldNotCallNebulaWhenNotTranscribing() {
            // Given: Unit is idle (not transcribing)
            testState.setPhase(ProcessingPhase.IDLE);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.cancelProcessing(testUnit.getId());

            // Then: Should NOT call Nebula cancel
            verify(transcriptionApi, never()).cancelNebulaTranscription(anyLong());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        }

        @Test
        void shouldCancelPyrisIngestionWhenIngesting() {
            // Given: Unit is currently ingesting
            testState.setPhase(ProcessingPhase.INGESTING);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.cancelPendingIngestion(anyLong())).thenReturn(true);

            // When
            service.cancelProcessing(testUnit.getId());

            // Then: Should call Pyris cancel, NOT Nebula cancel
            verify(irisLectureApi).cancelPendingIngestion(testUnit.getId());
            verify(transcriptionApi, never()).cancelNebulaTranscription(anyLong());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(testState.getErrorMessage()).isEqualTo("Cancelled");
        }

        @Test
        void shouldHandleNebulaCancelFailureGracefully() {
            // Given: Nebula cancel throws exception
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Nebula unavailable")).when(transcriptionApi).cancelNebulaTranscription(anyLong());

            // When
            service.cancelProcessing(testUnit.getId());

            // Then: Should still update local state despite Nebula failure
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(testState.getErrorMessage()).isEqualTo("Cancelled");
        }

        @Test
        void shouldDoNothingIfNoStateExists() {
            // Given
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());

            // When
            service.cancelProcessing(testUnit.getId());

            // Then
            verify(processingStateRepository, never()).save(any());
            verify(transcriptionApi, never()).cancelNebulaTranscription(anyLong());
        }
    }

    // ==================== FLOW 4: Transcription Complete ====================

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
        void shouldIncrementRetryCountOnFailure() {
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

            // When
            service.handleTranscriptionComplete(transcription);

            // Then
            assertThat(testState.getRetryCount()).isEqualTo(1);
        }

        @Test
        void shouldMarkAsFailedAfterMaxRetries() {
            // Given: Already at max retries
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(2); // Will become 3 after this failure
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
            // Given: Transcription failed but unit has PDF, at max retries
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(2); // Will become 3 after this failure, triggering fallback
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
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.handleIngestionComplete(testUnit.getId(), true);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldHandleCallbackWhenStateNotFound() {
            // Given: State was deleted (unit may have been removed during ingestion)
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());

            // When: Callback arrives for deleted unit
            service.handleIngestionComplete(testUnit.getId(), true);

            // Then: Should handle gracefully without exception
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldIgnoreStaleCallbackWhenNotIngesting() {
            // Given: State is in a different phase (callback may be stale)
            testState.setPhase(ProcessingPhase.DONE);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When: Stale callback arrives
            service.handleIngestionComplete(testUnit.getId(), true);

            // Then: Should ignore and not modify state
            verify(processingStateRepository, never()).save(any());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldMarkAsFailedAfterMaxIngestionRetries() {
            // Given
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setRetryCount(2); // Will become 3 after failure
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.handleIngestionComplete(testUnit.getId(), false);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        }
    }

    // ==================== FLOW 6: Retry Processing ====================

    @Nested
    class RetryProcessing {

        @Test
        void shouldRetryOnlyIfFailed() {
            // Given
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setRetryCount(3);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tumLiveApi.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://playlist.m3u8"));
            when(transcriptionApi.startNebulaTranscription(anyLong(), anyLong(), any())).thenReturn("new-job");

            // When
            service.retryProcessing(testUnit);

            // Then
            assertThat(testState.getRetryCount()).isEqualTo(0);
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

    // ==================== Service Not Available Scenarios ====================

    @Nested
    class ServiceNotAvailable {

        @Test
        void shouldSkipTranscriptionWhenNebulaNotAvailable() {
            // Given: Service created without transcription API
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.empty(), // No transcription API
                    Optional.of(tumLiveApi), Optional.of(irisLectureApi));

            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
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
                    Optional.of(irisLectureApi));

            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnitIdWithLock(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should go to ingestion without checking playlist
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
        }

        @Test
        void shouldNotCancelOnNebulaWhenApiNotAvailable() {
            // Given: Service created without transcription API
            service = new LectureContentProcessingService(processingStateRepository, transcriptionRepository, Optional.empty(), // No transcription API
                    Optional.of(tumLiveApi), Optional.of(irisLectureApi));

            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.cancelProcessing(testUnit.getId());

            // Then: Should still update local state
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            // No exception thrown, gracefully handled
        }
    }
}
