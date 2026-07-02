package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;

@ExtendWith(MockitoExtension.class)
class IrisLectureUnitSyncEventListenerTest {

    private static final long LECTURE_UNIT_ID = 42L;

    @Mock
    private AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    @Mock
    private IrisLectureUnitSyncStateRepository syncStateRepository;

    @Mock
    private LectureContentProcessingService contentProcessingService;

    private IrisLectureUnitSyncEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new IrisLectureUnitSyncEventListener(attachmentVideoUnitRepository, syncStateRepository, contentProcessingService);
    }

    @Test
    void visibilityDirtyEventDispatchesVisibilityUpdateAndMarksHashSynced() {
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        var state = syncState();
        state.setVisibilityHash("visibility-hash");
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        when(syncStateRepository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.of(state));

        listener.handleVisibilityDirty(new IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent(LECTURE_UNIT_ID));

        verify(contentProcessingService).triggerProcessingForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY);
        verify(syncStateRepository).save(state);
        assertThat(state.getLastSyncedVisibilityHash()).isEqualTo("visibility-hash");
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_CLEAN);
    }

    @Test
    void metadataDirtyEventKeepsRetryScheduledWhenVisibilityIsStillDirty() {
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

        verify(contentProcessingService).triggerProcessingForUpdateKind(unit, LectureContentUpdateKind.METADATA);
        verify(syncStateRepository).save(state);
        assertThat(state.getLastSyncedMetadataHash()).isEqualTo("metadata-hash");
        assertThat(state.getLastSyncedVisibilityHash()).isNull();
        assertThat(state.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_DIRTY);
        assertThat(state.getNextRetryAt()).isEqualTo(nextRetryAt);
    }

    private static IrisLectureUnitSyncState syncState() {
        var state = new IrisLectureUnitSyncState();
        state.setLectureUnitId(LECTURE_UNIT_ID);
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        return state;
    }
}
