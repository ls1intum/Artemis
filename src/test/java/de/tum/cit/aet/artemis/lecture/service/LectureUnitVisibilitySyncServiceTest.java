package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.LectureContentUpdateSnapshot;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class LectureUnitVisibilitySyncServiceTest {

    private static final long EXERCISE_ID = 9L;

    private static final long LECTURE_UNIT_ID = 42L;

    private static final ZonedDateTime RELEASE_DATE = ZonedDateTime.parse("2026-07-02T12:00:00Z");

    private static final ZonedDateTime HIDDEN_UNTIL = ZonedDateTime.parse("2026-07-03T12:00:00Z");

    @Mock
    private SlideRepository slideRepository;

    @Mock
    private IrisLectureUnitSyncService irisLectureUnitSyncService;

    private LectureUnitVisibilitySyncService service;

    @BeforeEach
    void setUp() {
        service = new LectureUnitVisibilitySyncService(slideRepository, irisLectureUnitSyncService);
    }

    @Test
    void marksAffectedUnitDirtyWithDetachedSnapshotContainingMetadataAndFullSortedSlideVisibility() {
        var exercise = exercise();
        var unit = attachmentVideoUnit();
        when(slideRepository.findByExerciseId(EXERCISE_ID)).thenReturn(List.of(slide(2, HIDDEN_UNTIL, unit)));
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID))
                .thenReturn(List.of(slide(3, null, unit), slide(1, HIDDEN_UNTIL.plusDays(1), unit), slide(2, HIDDEN_UNTIL, unit)));

        service.markVisibilityDirtyForExercise(exercise);

        var snapshotCaptor = ArgumentCaptor.forClass(LectureContentUpdateSnapshot.class);
        verify(irisLectureUnitSyncService).markVisibilityDirtyAfterCommit(snapshotCaptor.capture());
        var snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.lectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(snapshot.lectureUnitName()).isEqualTo("Exercise slides");
        assertThat(snapshot.lectureName()).isEqualTo("Lecture 1");
        assertThat(snapshot.courseName()).isEqualTo("Course");
        assertThat(snapshot.courseDescription()).isEqualTo("Course description");
        assertThat(snapshot.attachmentVersion()).isEqualTo(7);
        assertThat(snapshot.attachmentLink()).isEqualTo("attachments/unit.pdf");
        assertThat(snapshot.videoSource()).isEqualTo("https://video.example/source");
        assertThat(snapshot.releaseDate()).isEqualTo(RELEASE_DATE);
        assertThat(snapshot.slideHiddenUntilBySlideNumber().keySet()).containsExactly(1, 2, 3);
        assertThat(snapshot.slideHiddenUntilBySlideNumber()).containsEntry(1, HIDDEN_UNTIL.plusDays(1)).containsEntry(2, HIDDEN_UNTIL).containsEntry(3, null);
    }

    @Test
    void deduplicatesMultipleLinkedSlidesBelongingToSameUnit() {
        var exercise = exercise();
        var firstUnitReference = attachmentVideoUnit();
        var secondUnitReference = attachmentVideoUnit();
        when(slideRepository.findByExerciseId(EXERCISE_ID)).thenReturn(List.of(slide(1, HIDDEN_UNTIL, firstUnitReference), slide(2, HIDDEN_UNTIL, secondUnitReference)));
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID))
                .thenReturn(List.of(slide(1, HIDDEN_UNTIL, firstUnitReference), slide(2, HIDDEN_UNTIL, firstUnitReference)));

        service.markVisibilityDirtyForExercise(exercise);

        verify(slideRepository).findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID);
        verify(irisLectureUnitSyncService).markVisibilityDirtyAfterCommit(any(LectureContentUpdateSnapshot.class));
    }

    @Test
    void doesNothingWhenNoSlidesAreLinkedToExercise() {
        var exercise = exercise();
        when(slideRepository.findByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        service.markVisibilityDirtyForExercise(exercise);

        verify(slideRepository, never()).findAllByAttachmentVideoUnitId(any());
        verifyNoInteractions(irisLectureUnitSyncService);
    }

    private static TextExercise exercise() {
        var exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        return exercise;
    }

    private static AttachmentVideoUnit attachmentVideoUnit() {
        var course = new Course();
        course.setTitle("Course");
        course.setDescription("Course description");

        var lecture = new Lecture();
        lecture.setTitle("Lecture 1");
        lecture.setCourse(course);

        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        unit.setName("Exercise slides");
        unit.setLecture(lecture);
        unit.setReleaseDate(RELEASE_DATE);
        unit.setVideoSource("https://video.example/source");

        var attachment = new Attachment();
        attachment.setVersion(7);
        attachment.setLink("attachments/unit.pdf");
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);

        return unit;
    }

    private static Slide slide(int slideNumber, ZonedDateTime hidden, AttachmentVideoUnit unit) {
        var slide = new Slide();
        slide.setSlideNumber(slideNumber);
        slide.setHidden(hidden);
        slide.setAttachmentVideoUnit(unit);
        return slide;
    }
}
