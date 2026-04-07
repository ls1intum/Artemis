package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Unit tests for {@link LectureContentProcessingService}.
 * Tests the automated lecture content processing pipeline including:
 * - PDF ingestion triggering
 * - State transitions
 * - Retry logic
 * - Content change detection
 */
class LectureContentProcessingServiceTest {

    private static final String TEST_JOB_TOKEN = "test-ingestion-token-123";

    private LectureContentProcessingService service;

    private ProcessingStateCallbackService processingStateCallbackService;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private IrisLectureApi irisLectureApi;

    private FeatureToggleService featureToggleService;

    private AttachmentVideoUnit testUnit;

    private Lecture testLecture;

    private LectureUnitProcessingState testState;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        irisLectureApi = mock(IrisLectureApi.class);
        featureToggleService = mock(FeatureToggleService.class);

        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);

        processingStateCallbackService = new ProcessingStateCallbackService(processingStateRepository, Optional.of(irisLectureApi));

        service = new LectureContentProcessingService(processingStateRepository, Optional.of(irisLectureApi), featureToggleService, processingStateCallbackService);

        testLecture = new Lecture();
        testLecture.setId(1L);
        testLecture.setTitle("Test Lecture");

        testUnit = new AttachmentVideoUnit();
        testUnit.setId(100L);
        testUnit.setLecture(testLecture);

        testState = new LectureUnitProcessingState(testUnit);
        testState.setPhase(ProcessingPhase.IDLE);
    }

    // ==================== FLOW 1: Trigger Processing ====================

    @Nested
    class TriggerProcessing {

        @Test
        void shouldGoToIngestionForPdfUnit() {
            // Given: Unit with PDF
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            service.triggerProcessing(testUnit);

            // Then
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
        void shouldSkipPdfUnitWhenIrisNotAvailable() {
            // Given: Service without Iris, unit has only PDF
            ProcessingStateCallbackService callbackService = new ProcessingStateCallbackService(processingStateRepository, Optional.empty());
            service = new LectureContentProcessingService(processingStateRepository, Optional.empty(), featureToggleService, callbackService);

            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            // When
            service.triggerProcessing(testUnit);

            // Then: No state saved, no APIs called
            verify(processingStateRepository, never()).save(any());
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

        @Test
        void shouldSkipProcessingWhenFeatureDisabled() {
            // Given
            when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(false);

            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            testUnit.setAttachment(pdfAttachment);

            // When
            service.triggerProcessing(testUnit);

            // Then
            verify(processingStateRepository, never()).save(any());
        }
    }

    // ==================== FLOW 2: Content Changed ====================

    @Nested
    class ContentChanged {

        @Test
        void shouldDeleteFromPyrisWhenAttachmentVersionChanges() {
            // Given: Existing state with old attachment version
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(2); // Changed from version 1
            testUnit.setVideoSource(null);
            testUnit.setAttachment(pdfAttachment);

            testState.setAttachmentVersion(1); // Old version
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("new-job");

            // When
            service.triggerProcessing(testUnit);

            // Then: Should delete from Pyris and re-ingest
            verify(irisLectureApi).deleteLectureFromPyrisDB(any());
            verify(irisLectureApi).addLectureUnitToPyrisDB(testUnit);
        }

        @Test
        void shouldNotReprocessIfContentUnchanged() {
            // Given: Same attachment version, already done
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            testState.setAttachmentVersion(1); // Same version
            testState.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When
            service.triggerProcessing(testUnit);

            // Then: Should not restart processing
            verify(irisLectureApi, never()).addLectureUnitToPyrisDB(any());
        }
    }

    // ==================== FLOW 3: Ingestion Complete ====================

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
            processingStateCallbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
            assertThat(testState.getIngestionJobToken()).isNull(); // Token cleared after success
        }

        @Test
        void shouldHandleCallbackWhenStateNotFound() {
            // Given: State was deleted
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());

            // When
            processingStateCallbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true);

            // Then: Should handle gracefully without exception
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldIgnoreStaleCallbackWhenNotIngesting() {
            // Given
            testState.setPhase(ProcessingPhase.DONE);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When
            processingStateCallbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, true);

            // Then
            verify(processingStateRepository, never()).save(any());
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldIgnoreStaleCallbackWithWrongToken() {
            // Given
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken("new-job-token");
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When
            processingStateCallbackService.handleIngestionComplete(testUnit.getId(), "old-job-token", true);

            // Then
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
            processingStateCallbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false);

            // Then
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        }
    }

    // ==================== FLOW 4: Retry Processing ====================

    @Nested
    class RetryProcessing {

        @Test
        void shouldRetryFailedPdfUnit() {
            // Given: Failed PDF-only unit
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setRetryCount(5);
            testState.setId(42L);
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState)).thenReturn(Optional.empty());
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("new-job");

            // When
            service.retryProcessing(testUnit);

            // Then: Old state deleted, fresh state created and ingestion started
            verify(processingStateRepository).delete(testState);
            verify(irisLectureApi).addLectureUnitToPyrisDB(any());
        }

        @Test
        void shouldNotRetryIfNotFailed() {
            // Given
            testState.setPhase(ProcessingPhase.DONE);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When
            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            // Then
            assertThat(result).isNull();
            verify(irisLectureApi, never()).addLectureUnitToPyrisDB(any());
        }

        @Test
        void shouldReturnNullWhenNoStateExists() {
            // Given
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.empty());

            // When
            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            // Then
            assertThat(result).isNull();
            verify(processingStateRepository, never()).delete(any());
        }

        @Test
        void shouldReturnNullWhenNoProcessingPossible() {
            // Given: Failed state but unit has no content
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setId(42L);
            testUnit.setVideoSource(null);
            testUnit.setAttachment(null);

            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));

            // When
            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            // Then: State preserved (no delete), null returned
            assertThat(result).isNull();
            verify(processingStateRepository, never()).delete(any());
        }

        @Test
        void shouldReturnNullForNullInput() {
            LectureUnitProcessingState result = service.retryProcessing(null);

            assertThat(result).isNull();
            verify(processingStateRepository, never()).findByLectureUnit_Id(anyLong());
        }

        @Test
        void shouldReturnNullForUnsavedUnit() {
            testUnit.setId(null);

            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            assertThat(result).isNull();
            verify(processingStateRepository, never()).findByLectureUnit_Id(anyLong());
        }

        @Test
        void shouldReturnActualStateWhenIngestionStarts() {
            // Given: Failed state with PDF
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setId(42L);
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            AtomicReference<LectureUnitProcessingState> savedState = new AtomicReference<>();
            when(processingStateRepository.save(any(LectureUnitProcessingState.class))).thenAnswer(inv -> {
                savedState.set(inv.getArgument(0));
                return inv.getArgument(0);
            });
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState)).thenAnswer(inv -> Optional.ofNullable(savedState.get()));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("job-123");

            // When
            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            // Then: Should return actual state in INGESTING phase
            assertThat(result).isNotNull();
            assertThat(result.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        }

        @Test
        void shouldRetryPdfOnlyUnit() {
            // Given: Failed state with PDF only (no video)
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setId(42L);
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            AtomicReference<LectureUnitProcessingState> savedState = new AtomicReference<>();
            when(processingStateRepository.save(any(LectureUnitProcessingState.class))).thenAnswer(inv -> {
                savedState.set(inv.getArgument(0));
                return inv.getArgument(0);
            });
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState)).thenAnswer(inv -> Optional.ofNullable(savedState.get()));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When
            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        }

        @Test
        void shouldReturnDoneWhenIngestionReturnsNull() {
            // Given: Failed state with PDF only, ingestion returns null (not applicable for this course)
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setId(42L);
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            AtomicReference<LectureUnitProcessingState> savedState = new AtomicReference<>();
            when(processingStateRepository.save(any(LectureUnitProcessingState.class))).thenAnswer(inv -> {
                savedState.set(inv.getArgument(0));
                return inv.getArgument(0);
            });
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState)).thenAnswer(inv -> Optional.ofNullable(savedState.get()));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn(null);

            // When
            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldScheduleRetryWhenIngestionThrows() {
            // Given: Failed state with PDF only, ingestion throws
            testState.setPhase(ProcessingPhase.FAILED);
            testState.setId(42L);
            testUnit.setVideoSource(null);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            pdfAttachment.setVersion(1);
            testUnit.setAttachment(pdfAttachment);

            AtomicReference<LectureUnitProcessingState> savedState = new AtomicReference<>();
            when(processingStateRepository.save(any(LectureUnitProcessingState.class))).thenAnswer(inv -> {
                savedState.set(inv.getArgument(0));
                return inv.getArgument(0);
            });
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState)).thenAnswer(inv -> Optional.ofNullable(savedState.get()));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenThrow(new RuntimeException("Pyris error"));

            // When
            LectureUnitProcessingState result = service.retryProcessing(testUnit);

            // Then: INGESTING with retry scheduled
            assertThat(result).isNotNull();
            assertThat(result.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
            assertThat(result.getRetryCount()).isEqualTo(1);
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
        void shouldStayInIngestingPhaseOnFailureForSchedulerRetry() {
            // Given: Ingestion fails but not at max retries
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(TEST_JOB_TOKEN);
            testState.setRetryCount(1);
            when(processingStateRepository.findByLectureUnit_Id(testUnit.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            processingStateCallbackService.handleIngestionComplete(testUnit.getId(), TEST_JOB_TOKEN, false);

            // Then: Should stay in INGESTING phase (scheduler will retry with backoff)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
            assertThat(testState.getRetryCount()).isEqualTo(2);
            verify(irisLectureApi, never()).addLectureUnitToPyrisDB(any());
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

            // Then
            verify(irisLectureApi).addLectureUnitToPyrisDB(any());
        }

        @Test
        void shouldRecoverLegacyTranscribingStateWithPdfFallback() {
            // Given: Legacy TRANSCRIBING state (pre-migration) with PDF available
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(2);
            Attachment pdfAttachment = new Attachment();
            pdfAttachment.setLink("/path/to/file.pdf");
            testUnit.setAttachment(pdfAttachment);

            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(irisLectureApi.addLectureUnitToPyrisDB(any())).thenReturn("ingestion-job");

            // When: Scheduler calls retryTranscription for legacy state
            service.retryTranscription(testState);

            // Then: Should fall back to PDF ingestion
            verify(irisLectureApi).addLectureUnitToPyrisDB(any());
        }

        @Test
        void shouldMarkLegacyTranscribingStateAsFailedWhenNoPdf() {
            // Given: Legacy TRANSCRIBING state with no PDF and Iris unavailable for video-only
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testUnit.setAttachment(null);

            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.retryTranscription(testState);

            // Then: Should mark as failed (can't recover without PDF)
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        }
    }
}
