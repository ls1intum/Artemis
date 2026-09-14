package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;

/**
 * Unit tests for {@link LectureIngestionReconcileService}: the walk over DONE/SKIPPED/stateless units,
 * the census-based divergence detection, orphan cleanup, and the lost-callback resolution.
 */
class LectureIngestionReconcileServiceTest {

    private static final long COURSE_ID = 7L;

    private static final String FINGERPRINT = "v1:current-fingerprint";

    private LectureIngestionReconcileService reconcileService;

    private LectureUnitProcessingStateRepository processingStateRepository;

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
        attachmentVideoUnitRepository = mock(AttachmentVideoUnitTestRepository.class);
        irisLectureApi = mock(IrisLectureApi.class);
        contentFingerprintService = mock(LectureUnitContentFingerprintService.class);
        processingService = mock(LectureContentProcessingService.class);

        reconcileService = new LectureIngestionReconcileService(processingStateRepository, attachmentVideoUnitRepository, Optional.of(irisLectureApi), contentFingerprintService,
                processingService, 5, 10, 0.8, Duration.ofHours(1));

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

        when(contentFingerprintService.computeFingerprint(unit)).thenReturn(FINGERPRINT);
        when(attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(COURSE_ID)).thenReturn(List.of(unit));
        when(processingStateRepository.findWithLectureUnitByCourseId(COURSE_ID)).thenReturn(List.of(state));
        // Every censused unit exists by default (no orphans); orphan tests override this. The null
        // guard keeps the answer safe when Mockito probes the method with null during re-stubbing.
        when(attachmentVideoUnitRepository.findExistingIds(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids == null ? Set.of() : new HashSet<>(ids);
        });
        // The requeue re-fetches by id and re-confirms the phase before writing; return the same
        // instance so the guard passes and the requeue mutates the state the assertions inspect.
        when(processingStateRepository.findById(state.getId())).thenReturn(Optional.of(state));
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
                3, 3, 0, 5, 1, 5, 0, 0, "en");
    }

    /**
     * A census entry with one clean generation and full coverage, varying only the structural fields the
     * completeness signals inspect: unit rows, chunk count, segment count, missing pages, and null displays.
     */
    private IngestionCensusUnitDTO structuralEntry(int unitRowCount, int chunkCount, int segmentCount, int missingPageCount, int nullDisplayCount) {
        return new IngestionCensusUnitDTO(lecture.getId(), unit.getId(), FINGERPRINT, unitRowCount, null, CURRENT_PIPELINE_VERSION, null, chunkCount, 1, 1, 5, 3, 3, 0,
                segmentCount, 1, 5, missingPageCount, nullDisplayCount, "en");
    }

    /** Make the unit an attachment (PDF) unit, so page chunks and slide segments are expected. */
    private void givenPdfUnit() {
        unit.setVideoSource(null);
        Attachment attachment = new Attachment();
        attachment.setLink("/attachments/lecture/1/file.pdf");
        unit.setAttachment(attachment);
    }

    private void givenCensus(IngestionCensusUnitDTO... entries) {
        when(irisLectureApi.getIngestionCensus(COURSE_ID)).thenReturn(new IngestionCensusDTO(COURSE_ID, CURRENT_PIPELINE_VERSION, List.of(entries)));
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
            int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

            assertThat(spent).isEqualTo(1);
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getErrorKey()).isNull();
            verify(processingStateRepository).save(state);
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
            verify(processingStateRepository).save(state);
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
            when(contentFingerprintService.computeFingerprint(unit)).thenThrow(new IllegalStateException("file missing"));
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
            when(contentFingerprintService.computeFingerprint(unit)).thenThrow(new IllegalArgumentException("Illegal character in path"));
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
            LectureUnitProcessingState liveState = new LectureUnitProcessingState(unit);
            liveState.setId(state.getId());
            liveState.setPhase(ProcessingPhase.TRANSCRIBING);
            when(processingStateRepository.findById(state.getId())).thenReturn(Optional.of(liveState));
            givenCensus();

            reconcileService.reconcileCourse(COURSE_ID, 10);

            verify(processingStateRepository, never()).save(any());
            assertThat(liveState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
        }

        @Test
        void shouldNotRequeueWhenAConcurrentCompletionReconfirmedTheUnit() {
            // The batch read saw a legacy DONE row (no confirmed fingerprint) and decided to requeue,
            // but a concurrent completion re-confirmed the unit (same DONE phase, real fingerprint)
            // before the write. The requeue must not revert that fresh confirmation.
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(null);
            LectureUnitProcessingState reconfirmed = new LectureUnitProcessingState(unit);
            reconfirmed.setId(state.getId());
            reconfirmed.setPhase(ProcessingPhase.DONE);
            reconfirmed.setConfirmedFingerprint(FINGERPRINT);
            when(processingStateRepository.findById(state.getId())).thenReturn(Optional.of(reconfirmed));
            givenCensus();

            reconcileService.reconcileCourse(COURSE_ID, 10);

            verify(processingStateRepository, never()).save(any());
            assertThat(reconfirmed.getPhase()).isEqualTo(ProcessingPhase.DONE);
            assertThat(reconfirmed.getConfirmedFingerprint()).isEqualTo(FINGERPRINT);
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

            when(contentFingerprintService.computeFingerprint(secondUnit)).thenReturn(FINGERPRINT);
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
        void shouldWrapTheCursorAfterAFullPass() {
            // One answer per cursor position; a second when() stub would consume the first
            // sequential answer during its own stubbing invocation
            java.util.concurrent.atomic.AtomicBoolean firstPass = new java.util.concurrent.atomic.AtomicBoolean(true);
            when(attachmentVideoUnitRepository.findCourseIdsWithAttachmentVideoUnitsAfter(anyLong(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(0).equals(0L) && firstPass.getAndSet(false) ? List.of(COURSE_ID) : List.<Long>of());
            state.setPhase(ProcessingPhase.DONE);
            state.setConfirmedFingerprint(FINGERPRINT);
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            reconcileService.walkNextCourses();
            // Second walk continues after the walked course, finds nothing, and wraps
            reconcileService.walkNextCourses();
            // Third walk starts from the beginning again
            reconcileService.walkNextCourses();

            verify(attachmentVideoUnitRepository).findCourseIdsWithAttachmentVideoUnitsAfter(eq(COURSE_ID), any());
            verify(attachmentVideoUnitRepository, org.mockito.Mockito.times(2)).findCourseIdsWithAttachmentVideoUnitsAfter(eq(0L), any());
        }
    }

    @Nested
    class LostCallbackResolution {

        @BeforeEach
        void stuckState() {
            state.setPhase(ProcessingPhase.INGESTING);
            state.setContentFingerprint(FINGERPRINT);
            state.setRetryCount(2);
            state.setIngestionJobToken("token-123");
        }

        @Test
        void shouldRequeueWithoutRetryPenaltyWhenStampMatches() {
            givenCensus(censusEntry(unit.getId(), FINGERPRINT, 1));

            boolean resolved = reconcileService.resolveStuckIngestionWithoutRetryPenalty(state);

            assertThat(resolved).isTrue();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
            assertThat(state.getRetryCount()).isEqualTo(2);
            assertThat(state.getIngestionJobToken()).isNull();
            verify(processingStateRepository).save(state);
        }

        @Test
        void shouldNotResolveWhenStampDiffers() {
            givenCensus(censusEntry(unit.getId(), "v1:previous-run", 1));

            assertThat(reconcileService.resolveStuckIngestionWithoutRetryPenalty(state)).isFalse();
            assertThat(state.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        }

        @Test
        void shouldNotResolveWhenUnitAbsentFromCensus() {
            givenCensus();

            assertThat(reconcileService.resolveStuckIngestionWithoutRetryPenalty(state)).isFalse();
        }

        @Test
        void shouldNotResolveWithoutCensus() {
            when(irisLectureApi.getIngestionCensus(COURSE_ID)).thenReturn(null);

            assertThat(reconcileService.resolveStuckIngestionWithoutRetryPenalty(state)).isFalse();
        }

        @Test
        void shouldNotResolveWithoutDispatchedFingerprint() {
            state.setContentFingerprint(null);

            assertThat(reconcileService.resolveStuckIngestionWithoutRetryPenalty(state)).isFalse();
            verify(irisLectureApi, never()).getIngestionCensus(anyLong());
        }

        @Test
        void shouldNotResolveTranscribingStates() {
            state.setPhase(ProcessingPhase.TRANSCRIBING);

            assertThat(reconcileService.resolveStuckIngestionWithoutRetryPenalty(state)).isFalse();
            verify(irisLectureApi, never()).getIngestionCensus(anyLong());
        }
    }

    @Test
    void shouldSpendNothingWithoutIrisApi() {
        LectureIngestionReconcileService withoutIris = new LectureIngestionReconcileService(processingStateRepository, attachmentVideoUnitRepository, Optional.empty(),
                contentFingerprintService, processingService, 5, 10, 0.8, Duration.ofHours(1));

        assertThat(withoutIris.walkNextCourses()).isZero();
        assertThat(withoutIris.resolveStuckIngestionWithoutRetryPenalty(state)).isFalse();
    }

    /**
     * The attachment-only variant of the content guard: a unit with a PDF but no video must be walked.
     */
    @Test
    void shouldReconcileAttachmentOnlyUnits() {
        unit.setVideoSource(null);
        Attachment attachment = new Attachment();
        attachment.setLink("/attachments/lecture/1/file.pdf");
        unit.setAttachment(attachment);
        state.setPhase(ProcessingPhase.DONE);
        state.setConfirmedFingerprint(null);
        givenCensus();

        int spent = reconcileService.reconcileCourse(COURSE_ID, 10);

        assertThat(spent).isEqualTo(1);
        assertThat(state.getPhase()).isEqualTo(ProcessingPhase.IDLE);
    }
}
