package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
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
        var unit = attachmentVideoUnit(List.of(slide(1, 1L, HIDDEN_UNTIL)));

        service.markMetadataDirtyAfterCommit(unit);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository).save(stateCaptor.capture());
        var state = stateCaptor.getValue();
        assertThat(state.getLectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(state.getMetadataHash()).hasSize(64);
        assertThat(state.getStatus()).isEqualTo("DIRTY");
        assertThat(state.getNextRetryAt()).isNotNull();

        var eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent.class)
                .extracting("lectureUnitId").isEqualTo(LECTURE_UNIT_ID);
    }

    @Test
    void markVisibilityDirtyCreatesStateAndPublishesEvent() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var unit = attachmentVideoUnit(List.of(slide(2, 22L, HIDDEN_UNTIL), slide(1, 11L, HIDDEN_UNTIL.plusDays(1))));

        service.markVisibilityDirtyAfterCommit(unit);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository).save(stateCaptor.capture());
        var state = stateCaptor.getValue();
        assertThat(state.getLectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(state.getVisibilityHash()).hasSize(64);
        assertThat(state.getStatus()).isEqualTo("DIRTY");
        assertThat(state.getNextRetryAt()).isNotNull();

        var eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent.class)
                .extracting("lectureUnitId").isEqualTo(LECTURE_UNIT_ID);
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
        var unit = attachmentVideoUnit(List.of(slide(1, 1L, HIDDEN_UNTIL)));

        service.markMetadataDirtyAfterCommit(unit);
        service.markMetadataDirtyAfterCommit(unit);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository, times(2)).save(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues().get(1)).isSameAs(stateCaptor.getAllValues().getFirst());
    }

    @Test
    void metadataHashIsDeterministicForEquivalentInput() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var firstUnit = attachmentVideoUnit(List.of(slide(1, 1L, HIDDEN_UNTIL)));
        var secondUnit = attachmentVideoUnit(List.of(slide(1, 1L, HIDDEN_UNTIL)));

        service.markMetadataDirtyAfterCommit(firstUnit);
        service.markMetadataDirtyAfterCommit(secondUnit);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository, times(2)).save(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues().getFirst().getMetadataHash()).isEqualTo(stateCaptor.getAllValues().get(1).getMetadataHash());
    }

    @Test
    void visibilityHashIsDeterministicForEquivalentInputAndChangesWithHiddenState() {
        when(repository.findByLectureUnitId(LECTURE_UNIT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var firstUnit = attachmentVideoUnit(List.of(slide(2, 22L, HIDDEN_UNTIL.plusDays(1)), slide(1, 11L, HIDDEN_UNTIL)));
        var equivalentUnitWithDifferentSlideOrder = attachmentVideoUnit(List.of(slide(1, 11L, HIDDEN_UNTIL), slide(2, 22L, HIDDEN_UNTIL.plusDays(1))));
        var changedUnit = attachmentVideoUnit(List.of(slide(1, 11L, HIDDEN_UNTIL.plusHours(1)), slide(2, 22L, HIDDEN_UNTIL.plusDays(1))));

        service.markVisibilityDirtyAfterCommit(firstUnit);
        service.markVisibilityDirtyAfterCommit(equivalentUnitWithDifferentSlideOrder);
        service.markVisibilityDirtyAfterCommit(changedUnit);

        var stateCaptor = ArgumentCaptor.forClass(IrisLectureUnitSyncState.class);
        verify(repository, times(3)).save(stateCaptor.capture());
        List<IrisLectureUnitSyncState> states = stateCaptor.getAllValues();
        assertThat(states.getFirst().getVisibilityHash()).isEqualTo(states.get(1).getVisibilityHash());
        assertThat(states.get(2).getVisibilityHash()).isNotEqualTo(states.getFirst().getVisibilityHash());
    }

    private static AttachmentVideoUnit attachmentVideoUnit(List<Slide> slides) {
        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        unit.setName("Exercise slides");
        unit.setReleaseDate(RELEASE_DATE);
        unit.setSlides(new ArrayList<>(slides));

        var lecture = new Lecture();
        lecture.setTitle("Lecture 1");
        lecture.setDescription("Lecture description");

        var course = new Course();
        course.setTitle("Course");
        course.setDescription("Course description");

        lecture.setCourse(course);
        unit.setLecture(lecture);
        return unit;
    }

    private static Slide slide(int slideNumber, Long id, ZonedDateTime hiddenUntil) {
        var slide = new Slide();
        slide.setId(id);
        slide.setSlideNumber(slideNumber);
        slide.setHidden(hiddenUntil);
        return slide;
    }
}
