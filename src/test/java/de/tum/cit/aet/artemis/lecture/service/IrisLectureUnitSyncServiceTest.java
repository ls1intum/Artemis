package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.dto.LectureContentUpdateSnapshot;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;

@ExtendWith(MockitoExtension.class)
class IrisLectureUnitSyncServiceTest {

    private static final long LECTURE_UNIT_ID = 42L;

    private static final ZonedDateTime RELEASE_DATE = ZonedDateTime.parse("2026-07-02T12:00:00Z");

    private static final ZonedDateTime HIDDEN_UNTIL = ZonedDateTime.parse("2026-07-03T12:00:00Z");

    @Mock
    private IrisLectureUnitSyncStateRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private IrisLectureUnitSyncService service;

    @BeforeEach
    void setUp() {
        service = new IrisLectureUnitSyncService(repository, eventPublisher);
    }

    @Test
    void markMetadataDirtyCreatesStateAndPublishesEvent() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var snapshot = snapshot();

        service.markMetadataDirtyAfterCommit(snapshot);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository).save(stateCaptor.capture());
        var state = stateCaptor.getValue();
        assertThat(state.getLectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(state.getMetadataHash()).hasSize(64);
        assertThat(state.getStatus()).isEqualTo("DIRTY");
        assertThat(state.getNextRetryAt()).isNotNull();

        var eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent.class).extracting("lectureUnitId").isEqualTo(LECTURE_UNIT_ID);
    }

    @Test
    void markVisibilityDirtyCreatesStateAndPublishesEvent() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var snapshot = snapshot(slideHiddenMap(2, HIDDEN_UNTIL, 1, HIDDEN_UNTIL.plusDays(1)));

        service.markVisibilityDirtyAfterCommit(snapshot);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository).save(stateCaptor.capture());
        var state = stateCaptor.getValue();
        assertThat(state.getLectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(state.getVisibilityHash()).hasSize(64);
        assertThat(state.getStatus()).isEqualTo("DIRTY");
        assertThat(state.getNextRetryAt()).isNotNull();

        var eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent.class).extracting("lectureUnitId")
                .isEqualTo(LECTURE_UNIT_ID);
    }

    @Test
    void markMetadataDirtyPublishesEventOnlyAfterActiveTransactionCommits() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.markMetadataDirtyAfterCommit(snapshot());

            verify(repository).save(any());
            verify(eventPublisher, never()).publishEvent(any(Object.class));

            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());

            var eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent.class).extracting("lectureUnitId")
                    .isEqualTo(LECTURE_UNIT_ID);
        }
        finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void markingDirtyAgainReusesExistingState() {
        AtomicReference<IrisLectureUnitSyncState> persistedState = new AtomicReference<>();
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenAnswer(invocation -> Optional.ofNullable(persistedState.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            IrisLectureUnitSyncState state = invocation.getArgument(0);
            persistedState.set(state);
            return state;
        });
        var snapshot = snapshot();

        service.markMetadataDirtyAfterCommit(snapshot);
        service.markMetadataDirtyAfterCommit(snapshot);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository, times(2)).save(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues().get(1)).isSameAs(stateCaptor.getAllValues().getFirst());
    }

    @Test
    void metadataHashIsDeterministicForEquivalentInput() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var firstSnapshot = snapshot();
        var secondSnapshot = snapshot();

        service.markMetadataDirtyAfterCommit(firstSnapshot);
        service.markMetadataDirtyAfterCommit(secondSnapshot);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository, times(2)).save(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues().getFirst().getMetadataHash()).isEqualTo(stateCaptor.getAllValues().get(1).getMetadataHash());
    }

    @Test
    void visibilityHashIsDeterministicForEquivalentInputAndChangesWithHiddenState() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var firstSnapshot = snapshot(slideHiddenMap(2, HIDDEN_UNTIL.plusDays(1), 1, HIDDEN_UNTIL));
        var equivalentSnapshotWithDifferentInsertionOrder = snapshot(slideHiddenMap(1, HIDDEN_UNTIL, 2, HIDDEN_UNTIL.plusDays(1)));
        var changedSnapshot = snapshot(slideHiddenMap(1, HIDDEN_UNTIL.plusHours(1), 2, HIDDEN_UNTIL.plusDays(1)));

        service.markVisibilityDirtyAfterCommit(firstSnapshot);
        service.markVisibilityDirtyAfterCommit(equivalentSnapshotWithDifferentInsertionOrder);
        service.markVisibilityDirtyAfterCommit(changedSnapshot);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository, times(3)).save(stateCaptor.capture());
        List<IrisLectureUnitSyncState> states = stateCaptor.getAllValues();
        assertThat(states.getFirst().getVisibilityHash()).isEqualTo(states.get(1).getVisibilityHash());
        assertThat(states.get(2).getVisibilityHash()).isNotEqualTo(states.getFirst().getVisibilityHash());
    }

    private static LectureContentUpdateSnapshot snapshot() {
        return snapshot(Map.of(1, HIDDEN_UNTIL));
    }

    private static LectureContentUpdateSnapshot snapshot(Map<Integer, ZonedDateTime> slideHiddenUntilBySlideNumber) {
        return new LectureContentUpdateSnapshot(LECTURE_UNIT_ID, "Exercise slides", "Lecture 1", "Course", "Course description", 7, "attachments/unit.pdf",
                "https://video.example/source", RELEASE_DATE, slideHiddenUntilBySlideNumber);
    }

    private static Map<Integer, ZonedDateTime> slideHiddenMap(int firstSlideNumber, ZonedDateTime firstHiddenUntil, int secondSlideNumber, ZonedDateTime secondHiddenUntil) {
        var slideHiddenMap = new LinkedHashMap<Integer, ZonedDateTime>();
        slideHiddenMap.put(firstSlideNumber, firstHiddenUntil);
        slideHiddenMap.put(secondSlideNumber, secondHiddenUntil);
        return slideHiddenMap;
    }
}
