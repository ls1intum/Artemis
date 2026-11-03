package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

@ExtendWith(MockitoExtension.class)
class LectureUnitVisibilitySyncServiceTest {

    private static final long LECTURE_UNIT_ID = 42L;

    private static final long OTHER_LECTURE_UNIT_ID = 43L;

    private static final ZonedDateTime RELEASE_DATE = ZonedDateTime.parse("2026-07-02T12:00:00Z");

    private static final ZonedDateTime HIDDEN_UNTIL = ZonedDateTime.parse("2026-07-03T12:00:00Z");

    @Mock
    private SlideTestRepository slideRepository;

    @Mock
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Mock
    private IrisLectureUnitSyncService irisLectureUnitSyncService;

    private LectureUnitVisibilitySyncService service;

    @BeforeEach
    void setUp() {
        service = new LectureUnitVisibilitySyncService(slideRepository, attachmentVideoUnitRepository, irisLectureUnitSyncService);
    }

    @Test
    void marksVisibilityDirtyFromTheCompleteSavedDeck() {
        var unit = attachmentVideoUnit(LECTURE_UNIT_ID);
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        // The repository returns the current deck only; slide 1 is hidden for an unrelated reason and must stay in the snapshot.
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(slide(2, null), slide(1, HIDDEN_UNTIL)));

        service.markVisibilityDirty(LECTURE_UNIT_ID);

        var snapshot = ArgumentCaptor.forClass(LectureContentUpdateSnapshot.class);
        verify(irisLectureUnitSyncService).markVisibilityDirty(snapshot.capture());
        assertThat(snapshot.getValue().lectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(snapshot.getValue().releaseDate()).isEqualTo(RELEASE_DATE);
        assertThat(snapshot.getValue().slideHiddenUntilBySlideNumber()).containsExactly(entry(1, HIDDEN_UNTIL), entry(2, null));
    }

    @Test
    void failureForOneUnitDoesNotStopTheOthers() {
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(LECTURE_UNIT_ID)).thenThrow(new IllegalStateException("database unavailable"));
        var otherUnit = attachmentVideoUnit(OTHER_LECTURE_UNIT_ID);
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(OTHER_LECTURE_UNIT_ID)).thenReturn(Optional.of(otherUnit));
        when(slideRepository.findAllByAttachmentVideoUnitId(OTHER_LECTURE_UNIT_ID)).thenReturn(List.of(slide(1, HIDDEN_UNTIL)));

        service.markVisibilityDirty(List.of(LECTURE_UNIT_ID, OTHER_LECTURE_UNIT_ID));

        var snapshot = ArgumentCaptor.forClass(LectureContentUpdateSnapshot.class);
        verify(irisLectureUnitSyncService).markVisibilityDirty(snapshot.capture());
        assertThat(snapshot.getValue().lectureUnitId()).isEqualTo(OTHER_LECTURE_UNIT_ID);
    }

    private static AttachmentVideoUnit attachmentVideoUnit(long id) {
        var course = new Course();
        course.setTitle("Course");

        var lecture = new Lecture();
        lecture.setTitle("Lecture 1");
        lecture.setCourse(course);

        var unit = new AttachmentVideoUnit();
        unit.setId(id);
        unit.setName("Exercise slides");
        unit.setLecture(lecture);
        unit.setReleaseDate(RELEASE_DATE);

        var attachment = new Attachment();
        attachment.setVersion(7);
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);
        return unit;
    }

    private static Slide slide(int slideNumber, ZonedDateTime hidden) {
        var slide = new Slide();
        slide.setSlideNumber(slideNumber);
        slide.setHidden(hidden);
        return slide;
    }
}
