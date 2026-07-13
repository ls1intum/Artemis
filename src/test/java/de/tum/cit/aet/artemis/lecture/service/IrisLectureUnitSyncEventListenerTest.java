package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;

@ExtendWith(MockitoExtension.class)
class IrisLectureUnitSyncEventListenerTest {

    private static final long LECTURE_UNIT_ID = 42L;

    @Mock
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Mock
    private IrisLectureUnitSyncStateRepository syncStateRepository;

    @Mock
    private IrisLectureUnitSyncDispatchService syncDispatchService;

    private IrisLectureUnitSyncEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new IrisLectureUnitSyncEventListener(attachmentVideoUnitRepository, syncStateRepository, syncDispatchService);
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
        when(syncDispatchService.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY, projectedVisibility)).thenReturn("visibility-hash");

        listener.handleVisibilityDirty(new IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent(LECTURE_UNIT_ID, projectedVisibility));

        verify(syncDispatchService).triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY, projectedVisibility);
        verify(syncStateRepository).updateWithLectureUnitLock(eq(LECTURE_UNIT_ID), any());
        assertThat(state.getLastSyncedVisibilityHash()).isEqualTo("visibility-hash");
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_CLEAN);
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
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));
        when(syncDispatchService.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY)).thenReturn("persisted-slide-hash");

        listener.retryDirtyStates();

        assertThat(state.getLastSyncedVisibilityHash()).isEqualTo("persisted-slide-hash");
        assertThat(state.getVisibilityHash()).isEqualTo("projected-visibility-hash");
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_DIRTY);
        assertThat(state.getNextRetryAt()).isNotNull();
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
