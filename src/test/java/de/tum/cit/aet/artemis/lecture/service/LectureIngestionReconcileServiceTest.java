package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateReconcileRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitContentFingerprintService.ContentFingerprints;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;

/**
 * Unit tests for {@link LectureIngestionReconcileService}: the walk over DONE/SKIPPED/stateless units,
 * the census-based divergence detection, orphan cleanup, and the lost-callback resolution.
 */
class LectureIngestionReconcileServiceTest {

    private static final long COURSE_ID = 7L;

    private static final String FINGERPRINT = "v1:current-fingerprint";

    private static final String FINGERPRINT_WITHOUT_VIDEO = "v1:current-fingerprint-without-video";

    private LectureIngestionReconcileService reconcileService;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private LectureUnitProcessingStateReconcileRepository reconcileStateRepository;

    private LectureTranscriptionRepository transcriptionRepository;

    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    private IrisLectureApi irisLectureApi;

    private LectureUnitContentFingerprintService contentFingerprintService;

    private LectureContentProcessingService processingService;

    private Course course;

    private Lecture lecture;

    private AttachmentVideoUnit unit;

    private LectureUnitProcessingState state;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        reconcileStateRepository = mock(LectureUnitProcessingStateReconcileRepository.class);
        transcriptionRepository = mock(LectureTranscriptionRepository.class);
        attachmentVideoUnitRepository = mock(AttachmentVideoUnitTestRepository.class);
        irisLectureApi = mock(IrisLectureApi.class);
        contentFingerprintService = mock(LectureUnitContentFingerprintService.class);
        processingService = mock(LectureContentProcessingService.class);
        // The content rule of the processing service: a stored PDF or a video link (every link of these tests is a supported source unless a
        // test says otherwise)
        when(processingService.hasProcessableContent(any())).thenAnswer(invocation -> {
            AttachmentVideoUnit checked = invocation.getArgument(0);
            return checked != null
                    && ((checked.getAttachment() != null && checked.getAttachment().isStoredPdf()) || (checked.getVideoSource() != null && !checked.getVideoSource().isBlank()));
        });

        reconcileService = new LectureIngestionReconcileService(processingStateRepository, reconcileStateRepository, attachmentVideoUnitRepository, Optional.of(irisLectureApi),
                contentFingerprintService, processingService, 5, 10, 0.8, Duration.ofHours(1), 10, transcriptionRepository, Duration.ofDays(730), Duration.ofHours(6),
                Duration.ofMinutes(30));

        course = new Course();
        course.setId(COURSE_ID);
        lecture = new Lecture();
        lecture.setId(1L);
        lecture.setCourse(course);

        unit = new AttachmentVideoUnit();
        unit.setId(100L);
        unit.setLecture(lecture);
        unit.setVideoSource("https://live.rbg.tum.de/w/course/12345");

        state = new LectureUnitProcessingState(unit);
        state.setId(1L);

        when(contentFingerprintService.computeFingerprints(unit)).thenReturn(new ContentFingerprints(FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO));
        when(attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(COURSE_ID)).thenReturn(List.of(unit));
        when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of(state));
        // Every censused unit exists by default (no orphans); orphan tests override this. The null
        // guard keeps the answer safe when Mockito probes the method with null during re-stubbing.
        when(attachmentVideoUnitRepository.findExistingIds(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids == null ? Set.of() : new HashSet<>(ids);
        });
        // The stuck-recovery path re-reads the row by id to tell an interrupted completion callback apart
        // from a run that simply moved on; return the same instance so that check sees the seeded state.
        when(processingStateRepository.findById(state.getId())).thenReturn(Optional.of(state));
        // The requeue is committed through a guarded update; 1 = the row is still as the batch read saw it.
        when(reconcileStateRepository.requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), anyInt(), any())).thenReturn(1);
        // A revival goes through its own guard, which additionally pins the error, timestamp and budget it judged.
        when(reconcileStateRepository.reviveFailedIfUnchanged(anyLong(), any(), any(), anyInt(), anyInt(), anyInt(), any())).thenReturn(1);
    }

    private static final int CURRENT_PIPELINE_VERSION = 3;

    private IngestionCensusUnitDTO censusEntry(long unitId, String fingerprint, int unitRowCount) {
        return censusEntry(unitId, fingerprint, unitRowCount, 10, null, CURRENT_PIPELINE_VERSION, null);
    }

    private IngestionCensusUnitDTO censusEntry(long unitId, String fingerprint, int unitRowCount, int chunkCount, Integer expectedChunkCount, Integer pipelineVersion,
            Double qualityScore) {
        return censusEntry(unitId, fingerprint, unitRowCount, chunkCount, 1, expectedChunkCount, pipelineVersion, qualityScore);
    }

    private IngestionCensusUnitDTO censusEntry(long unitId, String fingerprint, int unitRowCount, int chunkCount, int generationCount, Integer expectedChunkCount,
            Integer pipelineVersion, Double qualityScore) {
        // A structurally complete unit by default: contiguous page coverage (no missing pages), segments
        // present, no null display numbers. Divergence tests override one field to exercise a signal.
        return new IngestionCensusUnitDTO(lecture.getId(), unitId, fingerprint, unitRowCount, expectedChunkCount, pipelineVersion, qualityScore, chunkCount, generationCount, 1, 5,
                3, 3, 0, 5, 1, 5, 0, 0, "en", false);
    }

    /**
     * A census entry with one clean generation and full coverage, varying only the structural fields the
     * completeness signals inspect: unit rows, chunk count, segment count, missing pages, and null displays.
     */
    private IngestionCensusUnitDTO structuralEntry(int unitRowCount, int chunkCount, int segmentCount, int missingPageCount, int nullDisplayCount) {
        return new IngestionCensusUnitDTO(lecture.getId(), unit.getId(), FINGERPRINT, unitRowCount, null, CURRENT_PIPELINE_VERSION, null, chunkCount, 1, 1, 5, 3, 3, 0,
                segmentCount, 1, 5, missingPageCount, nullDisplayCount, "en", false);
    }

    /** Make the unit an attachment (PDF) unit, so page chunks and slide segments are expected. */
    private void givenPdfUnit() {
        unit.setVideoSource(null);
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        attachment.setLink("file.pdf");
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);
    }

    private void givenCensus(IngestionCensusUnitDTO... entries) {
        when(irisLectureApi.getIngestionCensus(COURSE_ID)).thenReturn(new IngestionCensusDTO(COURSE_ID, CURRENT_PIPELINE_VERSION, List.of(entries), false));
    }

    @Nested
    class FailedUnitRevival {

        @BeforeEach
        void markFailed() {
            state.setPhase(ProcessingPhase.FAILED);
            state.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
            state.setLastUpdated(ZonedDateTime.now().minusHours(2));
            givenCensus();
        }

        @Test
        void shouldReviveTransientFailureAfterCooldownIntoIdle() {
            // Captured before the act: a successful revival requeues the snapshot, which clears the error key and
            // rewrites the timestamp, so reading them afterwards would not be what the guard was actually given.
            String observedErrorKey = state.getErrorKey();
            ZonedDateTime observedLastUpdated = state.getLastUpdated();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getErrorKey()).isNull();
            // Committed through the revival guard, which pins the three FAILED facts that authorized it: a unit
            // that failed again for another reason before this write must not have that newer failure erased.
            verify(reconcileStateRepository).reviveFailedIfUnchanged(eq(state.getId()), eq(observedErrorKey), eq(observedLastUpdated), eq(0), eq(1), anyInt(), any());
        }

        @Test
        void shouldNotReviveBeforeTheCooldownElapses() {
            state.setLastUpdated(ZonedDateTime.now());

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldNeverReviveAPermanentContentFailure() {
            state.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate");

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldNotReviveWhenTheStoreIsUnavailable() {
            when(irisLectureApi.getIngestionCensus(COURSE_ID)).thenReturn(null);

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldCountEachRevival() {
            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            // The revival budget is consumed by one so a unit that keeps failing is eventually bounded.
            assertThat(state.getRevivalCount()).isEqualTo(1);
        }

        @Test
        void shouldNotReviveAfterExhaustingTheRevivalBudget() {
            // Already revived MAX_REVIVALS (10) times without ever succeeding: a generic error that keeps
            // recurring is treated as effectively permanent and left FAILED for the manual retry button.
            state.setRevivalCount(10);

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            verify(processingStateRepository, never()).save(any());
        }
    }

    @Nested
    class DoneUnits {

        @Test
        void shouldLeaveVerifiedUnitAlone() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldRequeueLegacyUnitWithoutConfirmedFingerprint() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            state.setRetryCount(3);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getRetryCount()).isZero();
            // A legacy row carries no confirmed fingerprint, so that null is what the guard pins and what it keeps.
            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), isNull(), isNull(), any(), any(), anyInt(), any());
        }

        @Test
        void shouldHandAChangedVideoToTheContentChangePathInsteadOfRequeueing() {
            // The video changed without the update path firing, and the previous video's completed transcript is still
            // stored. A plain requeue would dispatch straight to INGESTING with that transcript; the content-change path
            // deletes it first.
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint("v1:old-fingerprint");
            givenCensus(censusEntry(unit.getId(), "v1:old-fingerprint", 1));
            when(processingService.hasVideoSourceChanged(unit, state)).thenReturn(true);

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            verify(processingService).triggerProcessingAsBacklog(unit);
            verify(reconcileStateRepository, never()).requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldRequeueWhenContentChangedSinceConfirmation() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint("v1:old-fingerprint");
            givenCensus(censusEntry(unit.getId(), "v1:old-fingerprint", 1));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        }

        @Test
        void shouldRequeueAndClearConfirmationWhenIndexLostTheData() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // Census has no entry for the unit at all: the index lost the rows
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getConfirmedFingerprint()).isNull();
        }

        @Test
        void shouldRequeueWhenIndexStampDiffersFromConfirmation() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), "v1:some-other-stamp", 1));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getConfirmedFingerprint()).isNull();
        }

        @Test
        void shouldRequeueAndForceReingestWhenChunkCountFallsBelowExpectation() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // 10 chunks stored, but the certified run recorded 12: rows were lost (a shortfall).
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1, 10, 1, 12, CURRENT_PIPELINE_VERSION, null));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getConfirmedFingerprint()).isNull();
            // The re-queue must force a full re-ingest, or the skip-check would treat the unit as complete.
            assertThat(state.isForceReingest()).isTrue();
        }

        @Test
        void shouldRequeueAndForceReingestWhenGenerationsCoexist() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // Two ingestion generations coexist (stale generation not swept): the authoritative dirty signal.
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1, 14, 2, 12, CURRENT_PIPELINE_VERSION, null));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getConfirmedFingerprint()).isNull();
            assertThat(state.isForceReingest()).isTrue();
        }

        @Test
        void shouldNotRequeueSingleGenerationCountDrift() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // One clean generation, but the live count exceeds a stale certified expectation. This is benign
            // (a stale expectation, not lost data or coexisting generations), so it must NOT be re-queued —
            // re-queuing it is exactly the churn we are eliminating.
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1, 14, 1, 12, CURRENT_PIPELINE_VERSION, null));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(0);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.DONE);
            assertThat(state.getConfirmedFingerprint()).isEqualTo(FINGERPRINT);
        }

        @Test
        void shouldRequeueAndForceReingestWhenUnitRowsCoexist() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // Two unit rows coexist (a crash between the unit-row write and its purge).
            givenCensus(structuralEntry(2, 10, 5, 0, 0));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.isForceReingest()).isTrue();
        }

        @Test
        void shouldRequeueAndForceReingestWhenAPdfUnitHasNoChunks() {
            givenPdfUnit();
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // DONE with a PDF but no page chunks: the content was lost or never written. Independent of the
            // certified expectation (null here), so a legacy/pre-ledger empty unit is still healed.
            givenCensus(structuralEntry(1, 0, 0, 0, 0));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.isForceReingest()).isTrue();
        }

        @Test
        void shouldNotExpectPageChunksFromAnExternalPdfLink() {
            // A video unit whose attachment links to a PDF hosted elsewhere is ingested as video-only, so the
            // index legitimately holds no page chunks or slide segments for it; that is not a divergence.
            Attachment attachment = new Attachment();
            attachment.setAttachmentType(AttachmentType.URL);
            attachment.setLink("https://example.org/lecture-notes.pdf");
            attachment.setAttachmentVideoUnit(unit);
            unit.setAttachment(attachment);
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(structuralEntry(1, 0, 0, 0, 0));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldRequeueAndForceReingestWhenPagesAreMissingFromCoverage() {
            givenPdfUnit();
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // A hole in the page coverage: some PDF page produced no chunk.
            givenCensus(structuralEntry(1, 40, 5, 2, 0));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.isForceReingest()).isTrue();
        }

        @Test
        void shouldRequeueAndForceReingestWhenSlideSegmentsAreMissing() {
            givenPdfUnit();
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // Slides present but their per-slide segment summaries are gone.
            givenCensus(structuralEntry(1, 40, 0, 0, 0));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.isForceReingest()).isTrue();
        }

        @Test
        void shouldRequeueAndForceReingestWhenDisplayPageNumbersAreUnresolved() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // Legacy chunks whose display page number was never resolved (null); a re-ingest repopulates them.
            givenCensus(structuralEntry(1, 40, 5, 0, 3));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.isForceReingest()).isTrue();
        }

        @Test
        void shouldNotRequeueACompleteUnit() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // One generation, one unit row, full coverage, segments present, no null displays: nothing to do.
            givenCensus(structuralEntry(1, 40, 5, 0, 0));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(0);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.DONE);
            assertThat(state.getConfirmedFingerprint()).isEqualTo(FINGERPRINT);
        }

        @Test
        void shouldRequeueLowQualityUnitsOncePerPipelineVersion() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // Stamped with an older pipeline version and a quality below the 0.8 threshold
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1, 10, 10, CURRENT_PIPELINE_VERSION - 1, 0.4));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            // The forced re-run bypasses Iris's skip checks; the version memory makes it once-per-version
            assertThat(state.isForceReingest()).isTrue();
            assertThat(state.getLastQualityPipelineVersion()).isEqualTo(CURRENT_PIPELINE_VERSION);
            assertThat(state.getDispatchPriority()).isEqualTo(LectureIngestionReconcileService.RECONCILE_DISPATCH_PRIORITY);
        }

        @Test
        void shouldNotRequeueLowQualityUnitsTwiceForTheSameVersion() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            state.setLastQualityPipelineVersion(CURRENT_PIPELINE_VERSION);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1, 10, 10, CURRENT_PIPELINE_VERSION - 1, 0.4));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            // Same version already attempted: the loop terminates instead of retrying forever
            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldNotQualityRequeueUnitsAlreadyOnTheCurrentVersion() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            // Low quality, but the stamp already carries the current version: re-running cannot improve it
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1, 10, 10, CURRENT_PIPELINE_VERSION, 0.4));

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
        }

        @Test
        void shouldNotTreatMissingCensusAsLostData() {
            // Census unavailable (old Iris or transient failure): only the content comparison may act
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            when(irisLectureApi.getIngestionCensus(COURSE_ID)).thenReturn(null);

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldSkipUnitWhoseFingerprintCannotBeComputed() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            when(contentFingerprintService.computeFingerprints(unit)).thenThrow(new IllegalStateException("file missing"));
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldSkipUnitWithMalformedLinkWithoutAbortingCourse() {
            // A malformed attachment link surfaces as IllegalArgumentException from URI.create, not
            // IllegalStateException. It must be caught (not escape and abort the walk).
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            when(contentFingerprintService.computeFingerprints(unit)).thenThrow(new IllegalArgumentException("Illegal character in path"));
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.DONE);
            verify(processingStateRepository, never()).save(any());
        }

        @Test
        void shouldNotRequeueWhenStateChangedSinceBatchRead() {
            // The batch read saw the unit as a divergent DONE, but a concurrent upload moved the live
            // row into TRANSCRIBING before the requeue write. The requeue must not clobber it.
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            // A concurrent upload moved the live row into TRANSCRIBING, so the guarded update matches no row.
            when(reconcileStateRepository.requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), anyInt(), any())).thenReturn(0);
            givenCensus();

            // Finding 3: a guard-rejected requeue must not count as spent, or it silently steals budget
            // from other units this same course walk should have gotten to.
            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            // The guard is re-asserted by the statement itself, so the decision's phase is what it must match on.
            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), any(), any(), any(), any(), anyInt(), any());
            assertThat(state.getPhase()).as("a rejected requeue must leave the snapshot untouched").isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldNotRequeueWhenAConcurrentCompletionReconfirmedTheUnit() {
            // The batch read saw a legacy DONE row (no confirmed fingerprint) and decided to requeue,
            // but a concurrent completion re-confirmed the unit (same DONE phase, real fingerprint)
            // before the write. The requeue must not revert that fresh confirmation.
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            // A concurrent completion re-confirmed the unit, so the fingerprint the guard pins no longer matches.
            when(reconcileStateRepository.requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), anyInt(), any())).thenReturn(0);
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            // The fingerprint observed at batch-read time is what the statement pins, so a re-confirmation misses it.
            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), isNull(), any(), any(), any(), anyInt(), any());
            assertThat(state.getConfirmedFingerprint()).as("a rejected requeue must leave the snapshot untouched").isNull();
        }
    }

    @Nested
    class SkippedUnits {

        @Test
        void shouldRequeueSkippedUnitOnceProcessable() {
            state.setPhase(ProcessingPhase.SKIPPED);
            when(irisLectureApi.isLectureUnitProcessable(unit)).thenReturn(true);
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        }

        @Test
        void shouldLeaveSkippedUnitWhileNotProcessable() {
            state.setPhase(ProcessingPhase.SKIPPED);
            when(irisLectureApi.isLectureUnitProcessable(unit)).thenReturn(false);
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.SKIPPED);
        }
    }

    @Nested
    class StatelessAndInFlightUnits {

        @Test
        void shouldTriggerProcessingForUnitWithoutState() {
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of());
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            verify(processingService).triggerProcessingAsBacklog(unit);
        }

        @Test
        void shouldIgnoreUnitWithoutIngestibleContent() {
            unit.setVideoSource(null);
            unit.setAttachment(null);
            state.setPhase(ProcessingPhase.DONE);
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isZero();
            verify(processingService, never()).triggerProcessingAsBacklog(any());
        }

        @Test
        void shouldLeaveInFlightAndFailedUnitsToTheNormalMachinery() {
            for (ProcessingPhase phase : List.of(ProcessingPhase.IDLE, ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING, ProcessingPhase.FAILED)) {
                state.setPhase(phase);
                givenCensus();

                int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

                assertThat(spent).as("phase %s must not be touched", phase).isZero();
            }
            verify(processingStateRepository, never()).save(any());
        }
    }

    @Nested
    class OrphanCleanup {

        @Test
        void shouldDeleteCensusedUnitsThatNoLongerExist() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            long orphanUnitId = 999L;
            when(attachmentVideoUnitRepository.findExistingIds(any())).thenReturn(Set.of(unit.getId()));
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1), censusEntry(orphanUnitId, "v1:whatever", 1));

            reconcileService.reconcileCourse(COURSE_ID, 10);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<IngestionJobIdentityDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(irisLectureApi).deleteLectureUnitsByIdentity(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().getFirst().lectureUnitId()).isEqualTo(orphanUnitId);
        }

        @Test
        void shouldCleanUpACourseWhoseLastUnitWasDeletedWhileIrisWasUnavailable() {
            // The unit's one-shot Iris deletion failed, then the unit itself was deleted: the course has no units left,
            // but the walk still visits it and the census still reports the unit's rows, so they are deleted now.
            when(attachmentVideoUnitRepository.findReconcileCourseIdsNewestFirst()).thenReturn(List.of(COURSE_ID));
            when(attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(COURSE_ID)).thenReturn(List.of());
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of());
            when(attachmentVideoUnitRepository.findExistingIds(any())).thenReturn(Set.of());
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            reconcileService.walkNextCourses();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<IngestionJobIdentityDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(irisLectureApi).deleteLectureUnitsByIdentity(captor.capture());
            assertThat(captor.getValue()).singleElement().satisfies(identity -> {
                assertThat(identity.courseId()).isEqualTo(COURSE_ID);
                assertThat(identity.lectureId()).isEqualTo(lecture.getId());
                assertThat(identity.lectureUnitId()).isEqualTo(unit.getId());
            });
        }

        @Test
        void shouldDeleteRowsOfUnitsWhoseLectureBecameATutorialLecture() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            long orphanUnitId = 999L;
            long tutorialUnitId = 555L;
            long tutorialLectureId = 42L;
            when(attachmentVideoUnitRepository.findExistingIds(any())).thenReturn(Set.of(unit.getId(), tutorialUnitId));
            when(attachmentVideoUnitRepository.findTutorialLectureUnitIdentities(any()))
                    .thenReturn(List.of(new IngestionJobIdentityDTO(COURSE_ID, tutorialLectureId, tutorialUnitId)));
            // Only chunks of the tutorial unit are indexed, so the census does not know its lecture.
            IngestionCensusUnitDTO chunksOnly = new IngestionCensusUnitDTO(null, tutorialUnitId, null, 0, null, null, null, 10, 1, 1, 5, 3, 3, 0, 5, 1, 5, 0, 0, "en", false);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1), chunksOnly, censusEntry(orphanUnitId, "v1:whatever", 1));

            reconcileService.reconcileCourse(COURSE_ID, 10);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<IngestionJobIdentityDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(irisLectureApi).deleteLectureUnitsByIdentity(captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrder(new IngestionJobIdentityDTO(COURSE_ID, lecture.getId(), orphanUnitId),
                    new IngestionJobIdentityDTO(COURSE_ID, tutorialLectureId, tutorialUnitId));
        }

        @Test
        void shouldNotDeleteAnythingWhenAllCensusedUnitsExist() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            reconcileService.reconcileCourse(COURSE_ID, 10);

            verify(irisLectureApi, never()).deleteLectureUnitsByIdentity(any());
        }
    }

    @Nested
    class BudgetAndCursor {

        @Test
        void shouldRespectTheRequeueBudget() {
            // Two divergent units but a budget of one
            AttachmentVideoUnit secondUnit = new AttachmentVideoUnit();
            secondUnit.setId(101L);
            secondUnit.setLecture(lecture);
            secondUnit.setVideoSource("https://live.rbg.tum.de/w/course/67890");
            LectureUnitProcessingState secondState = new LectureUnitProcessingState(secondUnit);
            secondState.setId(2L);
            secondState.setPhase(ProcessingPhase.DONE);
            state.setPhase(ProcessingPhase.DONE);

            when(contentFingerprintService.computeFingerprints(secondUnit)).thenReturn(new ContentFingerprints(FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO));
            when(attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(COURSE_ID)).thenReturn(List.of(unit, secondUnit));
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of(state, secondState));
            when(processingStateRepository.findById(secondState.getId())).thenReturn(Optional.of(secondState));
            givenCensus();

            int spent = reconcileService.reconcileCourse(COURSE_ID, 1);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(secondState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldResumeAPausedCourseAfterItsLastVisitedUnitInsteadOfSkippingOrRestartingIt() {
            LectureIngestionReconcileService oneRequeuePerPass = new LectureIngestionReconcileService(processingStateRepository, reconcileStateRepository,
                    attachmentVideoUnitRepository, Optional.of(irisLectureApi), contentFingerprintService, processingService, 5, 1, 0.8, Duration.ofHours(1), 10,
                    transcriptionRepository, Duration.ofDays(730), Duration.ofHours(6), Duration.ofMinutes(30));
            AttachmentVideoUnit secondUnit = new AttachmentVideoUnit();
            secondUnit.setId(101L);
            secondUnit.setLecture(lecture);
            secondUnit.setVideoSource("https://live.rbg.tum.de/w/course/67890");
            LectureUnitProcessingState secondState = new LectureUnitProcessingState(secondUnit);
            secondState.setId(2L);
            secondState.setPhase(ProcessingPhase.DONE);
            state.setPhase(ProcessingPhase.DONE);
            when(contentFingerprintService.computeFingerprints(secondUnit)).thenReturn(new ContentFingerprints(FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO));
            when(attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(COURSE_ID)).thenReturn(List.of(unit, secondUnit));
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of(state, secondState));
            when(attachmentVideoUnitRepository.findReconcileCourseIdsNewestFirst()).thenReturn(List.of(COURSE_ID));
            givenCensus();

            assertThat(oneRequeuePerPass.walkNextCourses()).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(secondState.getPhase()).isEqualTo(ProcessingPhase.DONE);

            // The first unit re-diverges before the next pass, as one that never converges would. Restarting the
            // course would spend the budget on it again and never reach the second unit.
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);

            assertThat(oneRequeuePerPass.walkNextCourses()).isEqualTo(1);
            assertThat(secondState.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.DONE);
            // Both steps belong to one pass, which reads the course list once
            verify(attachmentVideoUnitRepository).findReconcileCourseIdsNewestFirst();
        }

        @Test
        void shouldNotTouchTheCursorWhileTheBacklogIsFull() {
            when(processingStateRepository.countOpenBackgroundUnits(anyInt(), any())).thenReturn(10L);

            assertThat(reconcileService.walkNextCourses()).isZero();

            verify(attachmentVideoUnitRepository, never()).findReconcileCourseIdsNewestFirst();
            verify(irisLectureApi, never()).getIngestionCensus(anyLong());
        }

        @Test
        void shouldSpendOnlyTheFreeBacklogRoom() {
            // A bound of 10 with 9 background units still open leaves room for one more
            AttachmentVideoUnit secondUnit = new AttachmentVideoUnit();
            secondUnit.setId(101L);
            secondUnit.setLecture(lecture);
            secondUnit.setVideoSource("https://live.rbg.tum.de/w/course/67890");
            LectureUnitProcessingState secondState = new LectureUnitProcessingState(secondUnit);
            secondState.setId(2L);
            secondState.setPhase(ProcessingPhase.DONE);
            state.setPhase(ProcessingPhase.DONE);
            when(contentFingerprintService.computeFingerprints(secondUnit)).thenReturn(new ContentFingerprints(FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO));
            when(attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(COURSE_ID)).thenReturn(List.of(unit, secondUnit));
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of(state, secondState));
            when(processingStateRepository.countOpenBackgroundUnits(anyInt(), any())).thenReturn(9L);
            when(attachmentVideoUnitRepository.findReconcileCourseIdsNewestFirst()).thenReturn(List.of(COURSE_ID));
            givenCensus();

            assertThat(reconcileService.backlogBudget()).isEqualTo(1);
            assertThat(reconcileService.walkNextCourses()).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(secondState.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldNotLetABackfillSpendTheRoomAWalkStepIsSpending() {
            // A walk step holds the room it read while it waits for a census; a backfill running meanwhile must not spend the same room
            int[] backfilled = { -1 };
            int walked = reconcileService.spendBacklog(room -> {
                backfilled[0] = reconcileService.spendBacklog(innerRoom -> innerRoom);
                return room;
            });

            assertThat(walked).isEqualTo(10);
            assertThat(backfilled[0]).as("the overlapping backfill queues nothing").isZero();
            // Released afterwards: the next run gets the room again
            assertThat(reconcileService.spendBacklog(room -> room)).isEqualTo(10);
        }

        @Test
        void shouldContinueOnlyAnActivePassAndEndItAtTheWrap() {
            when(attachmentVideoUnitRepository.findReconcileCourseIdsNewestFirst()).thenReturn(List.of(COURSE_ID));
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            assertThat(reconcileService.continuePass()).as("no pass is active before the first cron tick").isZero();
            verify(attachmentVideoUnitRepository, never()).findReconcileCourseIdsNewestFirst();

            reconcileService.startOrContinuePass();
            verify(attachmentVideoUnitRepository).findReconcileCourseIdsNewestFirst();
            verify(irisLectureApi).getIngestionCensus(COURSE_ID);

            // The continuation takes the next step, which finds no course left and ends the pass
            reconcileService.continuePass();
            reconcileService.continuePass();
            verify(attachmentVideoUnitRepository).findReconcileCourseIdsNewestFirst();
            verify(irisLectureApi).getIngestionCensus(COURSE_ID);

            // The next cron tick starts a new pass with the newest course, reading the course list again
            reconcileService.startOrContinuePass();
            verify(attachmentVideoUnitRepository, org.mockito.Mockito.times(2)).findReconcileCourseIdsNewestFirst();
            verify(irisLectureApi, org.mockito.Mockito.times(2)).getIngestionCensus(COURSE_ID);
        }

        @Test
        void shouldKeepTheCursorOfTheActivePassOnACronTick() {
            when(attachmentVideoUnitRepository.findReconcileCourseIdsNewestFirst()).thenReturn(List.of(COURSE_ID));
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            reconcileService.startOrContinuePass();
            // A cron tick while the pass is active continues after the walked course instead of starting over
            reconcileService.startOrContinuePass();

            verify(attachmentVideoUnitRepository).findReconcileCourseIdsNewestFirst();
            verify(irisLectureApi).getIngestionCensus(COURSE_ID);
        }

        @Test
        void shouldWrapTheCursorAfterAFullPass() {
            when(attachmentVideoUnitRepository.findReconcileCourseIdsNewestFirst()).thenReturn(List.of(COURSE_ID));
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            reconcileService.walkNextCourses();
            // Second walk finds no course left after the walked one and wraps
            reconcileService.walkNextCourses();
            // Third walk starts a new pass with the newest course
            reconcileService.walkNextCourses();

            verify(attachmentVideoUnitRepository, org.mockito.Mockito.times(2)).findReconcileCourseIdsNewestFirst();
            verify(irisLectureApi, org.mockito.Mockito.times(2)).getIngestionCensus(COURSE_ID);
        }

        @Test
        void shouldWalkTheCoursesInTheOrderOfTheCourseList() {
            // The repository returns the newest course first; the walk must keep that order instead of sorting by id
            long newerCourseId = COURSE_ID - 1;
            when(attachmentVideoUnitRepository.findReconcileCourseIdsNewestFirst()).thenReturn(List.of(newerCourseId, COURSE_ID));
            when(irisLectureApi.getIngestionCensus(newerCourseId)).thenReturn(new IngestionCensusDTO(newerCourseId, CURRENT_PIPELINE_VERSION, List.of(), false));
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            reconcileService.walkNextCourses();

            InOrder inOrder = inOrder(irisLectureApi);
            inOrder.verify(irisLectureApi).getIngestionCensus(newerCourseId);
            inOrder.verify(irisLectureApi).getIngestionCensus(COURSE_ID);
        }
    }

    @Nested
    class CourseWindow {

        @BeforeEach
        void endTheCourseLongAgo() {
            course.setEndDate(ZonedDateTime.now().minusYears(3));
        }

        @Test
        void shouldNotTriggerAnyIngestionForACourseThatEndedBeforeTheWindow() {
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of());
            givenCensus();

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();

            verify(processingService, never()).triggerProcessingAsBacklog(any());
        }

        @Test
        void shouldNotRequeueSkippedOrDivergedUnitsOfACourseThatEndedBeforeTheWindow() {
            for (ProcessingPhase phase : List.of(ProcessingPhase.SKIPPED, ProcessingPhase.DONE)) {
                state.setPhase(phase);
                state.setConfirmedFingerprint(null);
                givenCensus();

                assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).as("phase %s must not be requeued", phase).isZero();
                assertThat(state.getPhase()).isEqualTo(phase);
            }
            verify(reconcileStateRepository, never()).requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), anyInt(), any());
        }

        @Test
        void shouldStillCleanUpOrphanedRowsOfACourseThatEndedBeforeTheWindow() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            long orphanUnitId = 999L;
            when(attachmentVideoUnitRepository.findExistingIds(any())).thenReturn(Set.of(unit.getId()));
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1), censusEntry(orphanUnitId, "v1:whatever", 1));

            reconcileService.reconcileCourse(COURSE_ID, 10);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<IngestionJobIdentityDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(irisLectureApi).deleteLectureUnitsByIdentity(captor.capture());
            assertThat(captor.getValue()).singleElement().extracting(IngestionJobIdentityDTO::lectureUnitId).isEqualTo(orphanUnitId);
        }

        @Test
        void shouldTriggerIngestionForACourseThatEndedWithinTheWindow() {
            course.setEndDate(ZonedDateTime.now().minusYears(1));
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of());
            givenCensus();

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(processingService).triggerProcessingAsBacklog(unit);
        }
    }

    @Test
    void shouldSpendNothingWithoutIrisApi() {
        LectureIngestionReconcileService withoutIris = new LectureIngestionReconcileService(processingStateRepository, reconcileStateRepository, attachmentVideoUnitRepository,
                Optional.empty(), contentFingerprintService, processingService, 5, 10, 0.8, Duration.ofHours(1), 10, transcriptionRepository, Duration.ofDays(730),
                Duration.ofHours(6), Duration.ofMinutes(30));

        assertThat(withoutIris.walkNextCourses()).isZero();
    }

    /**
     * The attachment-only variant of the content guard: a unit with a PDF but no video must be walked.
     */
    @Test
    void shouldReconcileAttachmentOnlyUnits() {
        givenPdfUnit();
        state.setPhase(ProcessingPhase.DONE);
        state.setConfirmedFingerprint(null);
        givenCensus();

        int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

        assertThat(spent).isEqualTo(1);
        assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
    }

    @Nested
    class ReviewFixes {

        @Test
        void shouldForceARebuildWhenTheContentChangedSinceItsConfirmation() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint("v1:previous-content");
            givenCensus(censusEntry(unit.getId(), "v1:previous-content", 1));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            // The skip-check compares versions, not content, so only a forced rebuild replaces what the index holds
            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), eq("v1:previous-content"), isNull(), eq(Boolean.TRUE),
                    any(), anyInt(), any());
        }

        @Test
        void shouldRequeueALegacyRowWithoutForcing() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), isNull(), isNull(), isNull(), any(), anyInt(), any());
        }

        @Test
        void shouldRebuildWhenACompletedTranscriptIsNotIndexed() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));
            LectureTranscription transcription = new LectureTranscription("en", List.of(new LectureTranscriptionSegment(0.0, 5.0, "Hello", 1)), unit);
            transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
            when(transcriptionRepository.findAllByLectureUnit_IdInAndTranscriptionStatus(any(), eq(TranscriptionStatus.COMPLETED))).thenReturn(List.of(transcription));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), eq(FINGERPRINT), isNull(), eq(Boolean.TRUE), any(),
                    anyInt(), any());
        }

        @Test
        void shouldNotRequeueForATranscriptWithoutSegments() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));
            LectureTranscription silent = new LectureTranscription("en", List.of(), unit);
            silent.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
            when(transcriptionRepository.findAllByLectureUnit_IdInAndTranscriptionStatus(any(), eq(TranscriptionStatus.COMPLETED))).thenReturn(List.of(silent));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).as("an empty transcript indexes no rows, so it must not loop").isZero();
        }

        @Test
        void shouldSkipEveryCensusDecisionWhenTheCourseCensusIsTruncated() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            long orphanUnitId = 999L;
            // The unit appears only through its chunks: zero unit rows and no stamp, which would otherwise force a rebuild
            IngestionCensusUnitDTO chunksOnly = new IngestionCensusUnitDTO(lecture.getId(), unit.getId(), null, 0, null, null, null, 10, 1, 1, 5, 3, 3, 0, 5, 1, 5, 0, 0, "en",
                    false);
            when(irisLectureApi.getIngestionCensus(COURSE_ID))
                    .thenReturn(new IngestionCensusDTO(COURSE_ID, CURRENT_PIPELINE_VERSION, List.of(chunksOnly, censusEntry(orphanUnitId, "v1:whatever", 1)), true));
            when(attachmentVideoUnitRepository.findExistingIds(any())).thenReturn(Set.of(unit.getId()));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();

            verify(reconcileStateRepository, never()).requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), anyInt(), any());
            verify(irisLectureApi, never()).deleteLectureUnitsByIdentity(any());
        }

        @Test
        void shouldSkipTheCensusChecksOfATruncatedUnit() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            IngestionCensusUnitDTO truncated = new IngestionCensusUnitDTO(lecture.getId(), unit.getId(), FINGERPRINT, 1, 500, CURRENT_PIPELINE_VERSION, null, 10, 3, 1, 5, 3, 3, 0,
                    5, 1, 5, 4, 0, "en", true);
            givenCensus(truncated);

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).as("undercounted rows of a truncated unit are no evidence of divergence").isZero();
        }

        @Test
        void shouldNotReviveAFailedUnitWhoseCensusEntryIsTruncated() {
            state.setPhase(ProcessingPhase.FAILED);
            state.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
            state.setLastUpdated(ZonedDateTime.now().minusHours(2));
            givenCensus(new IngestionCensusUnitDTO(lecture.getId(), unit.getId(), FINGERPRINT, 1, null, CURRENT_PIPELINE_VERSION, null, 10, 1, 1, 5, 3, 3, 0, 5, 1, 5, 0, 0, "en",
                    true));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();

            verify(reconcileStateRepository, never()).reviveFailedIfUnchanged(anyLong(), any(), any(), anyInt(), anyInt(), anyInt(), any());
        }

        @Test
        void shouldNotDeleteAnOrphanWithoutALectureId() {
            long orphanUnitId = 998L;
            IngestionCensusUnitDTO orphan = new IngestionCensusUnitDTO(null, orphanUnitId, null, 0, null, null, null, 10, 1, 1, 5, 3, 3, 0, 5, 1, 5, 0, 0, "en", false);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1), orphan);
            when(attachmentVideoUnitRepository.findExistingIds(any())).thenReturn(Set.of(unit.getId()));

            reconcileService.reconcileCourse(COURSE_ID, 10);

            verify(irisLectureApi, never()).deleteLectureUnitsByIdentity(any());
        }

        @Test
        void shouldRetryTheCleanupOfRemovedContent() {
            unit.setVideoSource(null);
            unit.setAttachment(null);
            state.setPhase(ProcessingPhase.DONE);
            state.setVideoSourceHash("old-video-hash");
            givenCensus();
            when(processingService.retryRemovedContentCleanup(unit, state.getId())).thenReturn(true);

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(processingService).retryRemovedContentCleanup(unit, state.getId());
        }

        @Test
        void shouldCleanUpAUnitWhoseOnlyContentIsAnUnsupportedVideo() {
            // DONE before only supported videos counted; its markers still record the link, so the cleanup settles it
            unit.setVideoSource("https://example.com/recording");
            unit.setAttachment(null);
            when(processingService.hasProcessableContent(unit)).thenReturn(false);
            state.setPhase(ProcessingPhase.DONE);
            state.setVideoSourceHash("old-video-hash");
            givenCensus();
            when(processingService.retryRemovedContentCleanup(unit, state.getId())).thenReturn(true);

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(processingService).retryRemovedContentCleanup(unit, state.getId());
            verify(processingService, never()).triggerProcessingAsBacklog(any());
        }

        @Test
        void shouldNotTriggerAStatelessUnitWhoseOnlyContentIsAnUnsupportedVideo() {
            unit.setVideoSource("https://example.com/recording");
            unit.setAttachment(null);
            when(processingService.hasProcessableContent(unit)).thenReturn(false);
            when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of());
            givenCensus();

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();

            verify(processingService, never()).triggerProcessingAsBacklog(any());
        }

        @Test
        void shouldLeaveAContentlessUnitWithoutMarkersAlone() {
            unit.setVideoSource(null);
            unit.setAttachment(null);
            state.setPhase(ProcessingPhase.DONE);
            givenCensus();

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();

            verify(processingService, never()).retryRemovedContentCleanup(any(), anyLong());
        }
    }

    /**
     * A unit whose video could not be resolved is ingested without it and confirmed with the fingerprint of its content without the video. The walk keeps
     * checking it like any done unit and tries the video again on the slow recheck interval.
     */
    @Nested
    class VideoPending {

        private static final String VIDEO = "https://live.rbg.tum.de/w/course/12345";

        @BeforeEach
        void givenPendingUnit() {
            givenPdfUnit();
            unit.setVideoSource(VIDEO);
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT_WITHOUT_VIDEO);
            state.setVideoSourceHash("video-hash");
            state.setLastUpdated(ZonedDateTime.now().minusHours(7));
            when(processingService.hasSupportedVideo(unit)).thenReturn(true);
            when(transcriptionRepository.findByLectureUnit_Id(unit.getId())).thenReturn(Optional.empty());
            givenCensus(censusEntry(unit.getId(), FINGERPRINT_WITHOUT_VIDEO, 1));
        }

        @Test
        void shouldTryTheVideoAgainWithAPlainRequeueAfterTheRecheckInterval() {
            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            // Plain: the confirmation is kept and no rebuild is forced, so Iris skips the unchanged pages
            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), eq(FINGERPRINT_WITHOUT_VIDEO),
                    eq(FINGERPRINT_WITHOUT_VIDEO), isNull(), isNull(), anyInt(), any());
        }

        @Test
        void shouldWaitForTheRecheckInterval() {
            state.setLastUpdated(ZonedDateTime.now().minusHours(1));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();

            verify(reconcileStateRepository, never()).requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldCheckTheIndexAgainstTheFingerprintWithoutTheVideo() {
            // The index lost the unit's stamp: a forced rebuild, at once, not after the recheck interval
            state.setLastUpdated(ZonedDateTime.now().minusMinutes(5));
            givenCensus(censusEntry(unit.getId(), "v1:something-else", 1));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), eq(FINGERPRINT_WITHOUT_VIDEO), isNull(), eq(true),
                    isNull(), anyInt(), any());
        }

        @Test
        void shouldHandARemovedOrReplacedVideoToTheContentChangePath() {
            when(processingService.hasVideoSourceChanged(unit, state)).thenReturn(true);

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(processingService).triggerProcessingAsBacklog(unit);
            verify(reconcileStateRepository, never()).requeueForReconcileIfUnchanged(anyLong(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldTreatAChangedPdfAsAContentChange() {
            // Neither the full fingerprint nor the one without the video matches: the PDF changed
            when(contentFingerprintService.computeFingerprints(unit)).thenReturn(new ContentFingerprints("v1:new-full", "v1:new-without-video"));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), eq(FINGERPRINT_WITHOUT_VIDEO), isNull(), eq(true),
                    isNull(), anyInt(), any());
        }

        @Test
        void shouldNotTreatAUnitWithACompletedTranscriptAsPending() {
            // A completed transcript is confirmed with the full fingerprint; one confirmed without the video is out of date and rebuilt
            LectureTranscription transcription = new LectureTranscription();
            transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
            when(transcriptionRepository.findByLectureUnit_Id(unit.getId())).thenReturn(Optional.of(transcription));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), eq(FINGERPRINT_WITHOUT_VIDEO), isNull(), eq(true),
                    isNull(), anyInt(), any());
        }

        @Test
        void shouldTakeTheConservativePathForAMissingMarkerWhenOnlyTheFingerprintWithoutTheVideoMatches() {
            // Without a marker, the fingerprint without the video cannot prove the video is the same one a stored transcript came from
            state.setVideoSourceHash(null);
            when(processingService.hasVideoSourceChanged(unit, state)).thenReturn(true);

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(processingService).triggerProcessingAsBacklog(unit);
            verify(reconcileStateRepository, never()).recordMissingVideoSourceHash(anyLong(), any(), any());
        }
    }

    /** A missing video marker is filled in only when the confirmation of the full content proves the video is unchanged. */
    @Nested
    class MissingVideoMarker {

        @Test
        void shouldRecordTheMarkerAndKeepTheContentWhenTheFullFingerprintIsConfirmed() {
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            state.setVideoSourceHash(null);
            when(processingService.videoSourceHashOf(unit)).thenReturn("video-hash");
            when(processingService.hasVideoSourceChanged(unit, state)).thenReturn(true);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();

            verify(reconcileStateRepository).recordMissingVideoSourceHash(state.getId(), "video-hash", FINGERPRINT);
            verify(processingService, never()).triggerProcessingAsBacklog(any());
        }

        @Test
        void shouldKeepThePlainRequeueForALegacyRowWithoutConfirmationOrStamp() {
            givenPdfUnit();
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            givenCensus(censusEntry(unit.getId(), null, 1));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);

            verify(reconcileStateRepository).requeueForReconcileIfUnchanged(eq(state.getId()), eq(ProcessingPhase.DONE), isNull(), isNull(), isNull(), isNull(), anyInt(), any());
        }
    }

    /** Background intake pauses while units keep failing and none succeeds; failures caused by the unit itself never pause it. */
    @Nested
    class OutagePause {

        @Test
        void shouldPauseWhileUnitsFailAndNoneSucceeds() {
            when(processingStateRepository.countRecentOutageFailures(any(), any())).thenReturn(10L);
            when(processingStateRepository.countRecentSuccessfulIngestions(any())).thenReturn(0L);
            int[] spendCalls = { 0 };

            assertThat(reconcileService.spendBacklog(room -> ++spendCalls[0])).isZero();

            assertThat(spendCalls[0]).isZero();
        }

        @Test
        void shouldNotPauseWhileUnitsStillSucceed() {
            when(processingStateRepository.countRecentOutageFailures(any(), any())).thenReturn(10L);
            when(processingStateRepository.countRecentSuccessfulIngestions(any())).thenReturn(1L);

            assertThat(reconcileService.spendBacklog(room -> 1)).isEqualTo(1);
        }

        @Test
        void shouldIgnoreFailuresCausedByTheUnitItself() {
            ArgumentCaptor<Collection<String>> ignored = ArgumentCaptor.captor();
            when(processingStateRepository.countRecentOutageFailures(any(), ignored.capture())).thenReturn(0L);

            assertThat(reconcileService.spendBacklog(room -> 1)).isEqualTo(1);

            assertThat(ignored.getValue()).contains("artemisApp.attachmentVideoUnit.processing.error.videoSourceUnavailable",
                    "artemisApp.attachmentVideoUnit.processing.error.attachmentUnreadable", "artemisApp.attachmentVideoUnit.processing.error.invalidUnitType")
                    .doesNotContain("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        }
    }

    /** A unit whose only content is a video that cannot be loaded yet is tried again on the slow recheck interval, without a revival limit. */
    @Nested
    class VideoOnlyUnitWaitingForItsRecording {

        @BeforeEach
        void markFailed() {
            state.setPhase(ProcessingPhase.FAILED);
            state.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.videoSourceUnavailable");
            state.setRevivalCount(25);
            givenCensus();
        }

        @Test
        void shouldReviveAfterTheRecheckIntervalEvenPastTheRevivalLimit() {
            state.setLastUpdated(ZonedDateTime.now().minusHours(7));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            // Waiting for a recording does not spend the budget that later protects the unit's recovery from an Iris outage
            verify(reconcileStateRepository).reviveFailedIfUnchanged(eq(state.getId()), any(), any(), eq(25), eq(0), anyInt(), any());
            assertThat(state.getRevivalCount()).isEqualTo(25);
        }

        @Test
        void shouldStillReviveAGenericFailureAfterLongWaitingForTheRecording() {
            // Weeks of video waits left the budget untouched; when the recording is there and Iris then fails, the generic revival still applies
            state.setRevivalCount(0);
            state.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
            state.setLastUpdated(ZonedDateTime.now().minusHours(2));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isEqualTo(1);
            verify(reconcileStateRepository).reviveFailedIfUnchanged(eq(state.getId()), any(), any(), eq(0), eq(1), anyInt(), any());
        }

        @Test
        void shouldWaitForTheRecheckInterval() {
            state.setLastUpdated(ZonedDateTime.now().minusHours(2));

            assertThat(reconcileService.reconcileCourse(COURSE_ID, 10)).isZero();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        }
    }
}
