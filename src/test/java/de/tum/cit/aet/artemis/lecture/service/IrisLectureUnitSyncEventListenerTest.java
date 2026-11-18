package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.iris.api.dtos.LectureUnitSyncOutcome;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

@ExtendWith(MockitoExtension.class)
class IrisLectureUnitSyncEventListenerTest {

    private static final long LECTURE_UNIT_ID = 42L;

    @Mock
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Mock
    private IrisLectureUnitSyncStateRepository syncStateRepository;

    @Mock
    private IrisLectureUnitSyncDispatchService syncDispatchService;

    @Mock
    private SlideTestRepository slideRepository;

    @Mock
    private IrisLectureUnitSyncService syncService;

    private IrisLectureUnitSyncEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new IrisLectureUnitSyncEventListener(attachmentVideoUnitRepository, syncStateRepository, syncDispatchService, slideRepository, syncService);
        lenient().when(syncDispatchService.triggerSyncForUpdateKind(any(), eq(LectureContentUpdateKind.METADATA)))
                .thenReturn(new IrisLectureUnitSyncDispatchService.DispatchResult(LectureUnitSyncOutcome.DISPATCHED, null));
        lenient().when(syncStateRepository.claimRetry(eq(LECTURE_UNIT_ID), any(), any())).thenAnswer(_ -> syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID));
    }

    @Test
    void backfillInitializesVisibilityForActiveLegacyUnits() {
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        unit.setReleaseDate(ZonedDateTime.parse("2026-07-03T10:15:30Z"));
        var slide = new Slide();
        slide.setSlideNumber(2);
        slide.setHidden(ZonedDateTime.parse("2026-07-04T10:15:30Z"));
        when(attachmentVideoUnitRepository.findUnitsMissingIrisSyncStateFromActiveCourses(any(), any(Pageable.class))).thenReturn(List.of(unit));
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(slide));

        listener.backfillMissingSyncStates();

        var snapshotCaptor = org.mockito.ArgumentCaptor.forClass(LectureContentUpdateSnapshot.class);
        verify(syncService).markVisibilityDirty(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().lectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(snapshotCaptor.getValue().releaseDate().toInstant()).isEqualTo(unit.getReleaseDate().toInstant());
        assertThat(snapshotCaptor.getValue().slideHiddenUntilBySlideNumber()).containsEntry(2, slide.getHidden());
    }

    private void enableStateTransitions() {
        doAnswer(invocation -> {
            long lectureUnitId = invocation.getArgument(0);
            java.util.function.Consumer<IrisLectureUnitSyncState> transition = invocation.getArgument(1);
            syncStateRepository.findByLectureUnitId(lectureUnitId).ifPresent(transition);
            return null;
        }).when(syncStateRepository).updateWithLectureUnitLock(anyLong(), any());
    }

    @Test
    void visibilityDirtyEventDispatchesVisibilityUpdateAndMarksHashSynced() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var state = syncState();
        state.setVisibilityHash("visibility-hash");
        var projectedVisibility = Map.of(1, ZonedDateTime.parse("2026-07-03T10:15:30Z"));
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));
        when(syncDispatchService.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY, projectedVisibility))
                .thenReturn(new IrisLectureUnitSyncDispatchService.DispatchResult(LectureUnitSyncOutcome.DISPATCHED, "visibility-hash"));

        listener.handleVisibilityDirty(new IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent(LECTURE_UNIT_ID, projectedVisibility));

        verify(syncDispatchService).triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY, projectedVisibility);
        verify(syncStateRepository).updateWithLectureUnitLock(eq(LECTURE_UNIT_ID), any());
        assertThat(state.getLastSyncedVisibilityHash()).isEqualTo("visibility-hash");
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_CLEAN);
    }

    @Test
    void unitPyrisHasNotIngestedIsSettledRatherThanRetriedForever() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var state = syncState();
        state.setVisibilityHash("visibility-hash");
        // What claimRetry leaves behind for the duration of the request.
        state.setStatus(IrisLectureUnitSyncState.STATUS_IN_PROGRESS);
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));
        when(syncDispatchService.triggerSyncForUpdateKind(eq(unit), eq(LectureContentUpdateKind.VISIBILITY), any()))
                .thenReturn(new IrisLectureUnitSyncDispatchService.DispatchResult(LectureUnitSyncOutcome.NOT_INGESTED, null));

        listener.handleVisibilityDirty(new IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent(LECTURE_UNIT_ID, Map.of()));

        // No retry is scheduled: only an ingestion changes the answer, and that reopens the state itself.
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_NOT_INGESTED);
        assertThat(state.getNextRetryAt()).isNull();
        assertThat(state.getRetryCount()).isZero();
        assertThat(state.getLastSyncedVisibilityHash()).isNull();
    }

    @Test
    void notIngestedAnswerDoesNotSettleAStateThatIngestionReopenedMeanwhile() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var claimed = syncState();
        claimed.setVisibilityHash("visibility-hash");
        claimed.setStatus(IrisLectureUnitSyncState.STATUS_IN_PROGRESS);
        // The claim commits before the request leaves, so an ingestion can complete while Pyris is still being asked
        // and reopen the row. The answer then describes a unit Pyris did not hold at the time but does now.
        var reopened = syncState();
        reopened.setVisibilityHash("visibility-hash");
        reopened.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        reopened.setNextRetryAt(ZonedDateTime.now());
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(claimed), Optional.of(reopened));
        when(syncDispatchService.triggerSyncForUpdateKind(eq(unit), eq(LectureContentUpdateKind.VISIBILITY), any()))
                .thenReturn(new IrisLectureUnitSyncDispatchService.DispatchResult(LectureUnitSyncOutcome.NOT_INGESTED, null));

        listener.handleVisibilityDirty(new IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent(LECTURE_UNIT_ID, Map.of()));

        // Settling here would strand the unit: the backfill does not recreate a row that exists, and the ingestion
        // that would have reopened it has already run.
        assertThat(reopened.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_DIRTY);
        assertThat(reopened.getNextRetryAt()).isNotNull();
    }

    @Test
    void aFailingSecondLegDoesNotRestartTheRetriesOfASettledRow() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        // The metadata leg of this claim already settled the row, and the visibility leg is still dirty and now fails.
        var settled = syncState();
        settled.setVisibilityHash("visibility-hash");
        settled.setStatus(IrisLectureUnitSyncState.STATUS_NOT_INGESTED);
        settled.setLastErrorKey("NotIngestedInPyris");
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(settled));
        when(syncDispatchService.triggerSyncForUpdateKind(eq(unit), eq(LectureContentUpdateKind.VISIBILITY), any())).thenThrow(new IllegalStateException("Pyris is unreachable"));

        listener.handleVisibilityDirty(new IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent(LECTURE_UNIT_ID, Map.of()));

        assertThat(settled.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_NOT_INGESTED);
        assertThat(settled.getRetryCount()).isZero();
        assertThat(settled.getNextRetryAt()).isNull();
    }

    @Test
    void retriesStopOnceTheLimitIsReached() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var state = syncState();
        state.setMetadataHash("metadata-hash");
        state.setRetryCount(9);
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));
        when(syncDispatchService.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.METADATA)).thenThrow(new IllegalStateException("Pyris is unreachable"));

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_FAILED);
        assertThat(state.getRetryCount()).isEqualTo(10);
        // Out of the hot retry path, but not abandoned: an installation that lost Pyris for half a day recovers on its
        // own rather than needing every row to be reset by hand.
        assertThat(state.getNextRetryAt()).isAfter(ZonedDateTime.now().plusHours(23));
    }

    @Test
    void metadataDirtyEventKeepsRetryScheduledWhenVisibilityIsStillDirty() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var nextRetryAt = ZonedDateTime.now();
        var state = syncState();
        state.setMetadataHash("metadata-hash");
        state.setVisibilityHash("visibility-hash");
        state.setNextRetryAt(nextRetryAt);
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        verify(syncDispatchService).triggerSyncForUpdateKind(unit, LectureContentUpdateKind.METADATA);
        verify(syncStateRepository).updateWithLectureUnitLock(eq(LECTURE_UNIT_ID), any());
        assertThat(state.getLastSyncedMetadataHash()).isEqualTo("metadata-hash");
        assertThat(state.getLastSyncedVisibilityHash()).isNull();
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_DIRTY);
        assertThat(state.getNextRetryAt().toInstant()).isEqualTo(nextRetryAt.toInstant());
    }

    @Test
    void visibilityRetryKeepsProjectedHashDirtyWhilePersistedSlidesAreStale() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var state = syncState();
        state.setVisibilityHash("projected-visibility-hash");
        when(syncStateRepository.findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(any(), any())).thenReturn(List.of(state));
        when(syncStateRepository.claimRetry(eq(LECTURE_UNIT_ID), any(), any())).thenReturn(Optional.of(state));
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));
        when(syncDispatchService.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY))
                .thenReturn(new IrisLectureUnitSyncDispatchService.DispatchResult(LectureUnitSyncOutcome.DISPATCHED, "persisted-slide-hash"));

        listener.retryDirtyStates();

        verify(syncStateRepository).claimRetry(eq(LECTURE_UNIT_ID), any(), any());
        verify(syncDispatchService).triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY);
        assertThat(state.getLastSyncedVisibilityHash()).isEqualTo("persisted-slide-hash");
        assertThat(state.getVisibilityHash()).isEqualTo("projected-visibility-hash");
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_DIRTY);
        assertThat(state.getNextRetryAt()).isNotNull();
    }

    @Test
    void skippedDispatchRemainsDirtyForRetryAfterIrisIsEnabledAgain() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var state = syncState();
        state.setMetadataHash("metadata-hash");
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));
        when(syncDispatchService.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.METADATA)).thenReturn(
                new IrisLectureUnitSyncDispatchService.DispatchResult(LectureUnitSyncOutcome.SKIPPED, null),
                new IrisLectureUnitSyncDispatchService.DispatchResult(LectureUnitSyncOutcome.DISPATCHED, null));

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        assertThat(state.getLastSyncedMetadataHash()).isNull();
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_DIRTY);
        assertThat(state.getLastErrorKey()).isEqualTo("DispatchSkipped");
        assertThat(state.getNextRetryAt()).isNotNull();

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        assertThat(state.getLastSyncedMetadataHash()).isEqualTo("metadata-hash");
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_CLEAN);
    }

    @Test
    void metadataSyncPreservesConcurrentStateAcrossSuccessAndFailure() {
        enableStateTransitions();
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var dispatchedState = syncState();
        dispatchedState.setMetadataHash("dispatched-hash");
        var currentState = syncState();
        currentState.setMetadataHash("new-dirty-hash");
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(dispatchedState), Optional.of(currentState));

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        assertThat(currentState.getMetadataHash()).isEqualTo("new-dirty-hash");
        assertThat(currentState.getLastSyncedMetadataHash()).isEqualTo("dispatched-hash");
        assertThat(currentState.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_DIRTY);

        var failedState = syncState();
        failedState.setMetadataHash("clean-hash");
        var concurrentlyCleanedState = syncState();
        concurrentlyCleanedState.setMetadataHash("clean-hash");
        concurrentlyCleanedState.setLastSyncedMetadataHash("clean-hash");
        concurrentlyCleanedState.setStatus(IrisLectureUnitSyncState.STATUS_CLEAN);
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(failedState), Optional.of(concurrentlyCleanedState));
        doThrow(new IllegalStateException("Pyris unavailable")).when(syncDispatchService).triggerSyncForUpdateKind(unit, LectureContentUpdateKind.METADATA);

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        assertThat(concurrentlyCleanedState.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_CLEAN);
        assertThat(concurrentlyCleanedState.getRetryCount()).isZero();
        assertThat(concurrentlyCleanedState.getNextRetryAt()).isNull();

        var retryState = syncState();
        retryState.setMetadataHash("retry-hash");
        retryState.setRetryCount(5);
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(retryState));
        ZonedDateTime before = ZonedDateTime.now();

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        assertThat(retryState.getNextRetryAt().toInstant()).isBetween(before.plusMinutes(60).toInstant(), ZonedDateTime.now().plusMinutes(60).toInstant());
    }

    @Test
    void lookupAndPersistenceFailuresDoNotEscapeEventHandling() {
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var state = syncState();
        state.setMetadataHash("metadata-hash");
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state)).thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(Optional.of(state));
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.empty(), Optional.of(unit));

        listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID));

        verify(syncStateRepository).delete(state);
        verify(syncStateRepository, never()).updateWithLectureUnitLock(anyLong(), any());
        verifyNoInteractions(syncDispatchService);

        assertThatCode(() -> listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID))).doesNotThrowAnyException();
        verifyNoInteractions(syncDispatchService);

        doThrow(new IllegalStateException("database unavailable")).when(syncStateRepository).updateWithLectureUnitLock(anyLong(), any());

        assertThatCode(() -> listener.handleMetadataDirty(new IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent(LECTURE_UNIT_ID))).doesNotThrowAnyException();
    }

    private static IrisLectureUnitSyncState syncState() {
        var state = new IrisLectureUnitSyncState();
        state.setLectureUnitId(LECTURE_UNIT_ID);
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        return state;
    }
}
