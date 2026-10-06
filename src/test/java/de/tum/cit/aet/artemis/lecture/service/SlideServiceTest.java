package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class SlideServiceTest {

    private SlideTestRepository slideRepository;

    private SlideUnhideService slideUnhideService;

    private AttachmentService attachmentService;

    private LectureUnitVisibilitySyncService visibilitySyncService;

    private SlideService slideService;

    @BeforeEach
    void setUp() {
        slideRepository = mock(SlideTestRepository.class);
        slideUnhideService = mock(SlideUnhideService.class);
        attachmentService = mock(AttachmentService.class);
        visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        slideService = new SlideService(slideRepository, slideUnhideService, attachmentService, visibilitySyncService);
    }

    @Test
    void futureDueDateHidesSlidesThenUpdatesIrisThenRegeneratesStudentVersion() {
        var dueDate = ZonedDateTime.now().plusDays(7);
        var exercise = exercise(dueDate);
        var attachment = attachment(10L, 100L);
        var slide = slide(attachment, null, false);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(slide));

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(slide.getHidden()).isEqualTo(dueDate);
        var order = inOrder(slideRepository, slideUnhideService, visibilitySyncService, attachmentService);
        order.verify(slideRepository).saveAll(List.of(slide));
        order.verify(slideUnhideService).handleSlideHiddenUpdate(slide);
        order.verify(visibilitySyncService).markVisibilityDirty(Set.of(100L));
        order.verify(attachmentService).regenerateStudentVersionOrRemoveOutdated(attachment);
    }

    @Test
    void missingDueDateMakesSlidesVisible() {
        var exercise = exercise(null);
        var attachment = attachment(10L, 100L);
        var slide = slide(attachment, ZonedDateTime.now().plusDays(1), false);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(slide));

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(slide.getHidden()).isNull();
        verify(attachmentService).regenerateStudentVersionOrRemoveOutdated(attachment);
    }

    @Test
    void pastDueDateMakesSlidesVisible() {
        var exercise = exercise(ZonedDateTime.now().minusDays(1));
        var slide = slide(attachment(10L, 100L), ZonedDateTime.now().plusDays(1), false);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(slide));

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(slide.getHidden()).isNull();
    }

    @Test
    void movedHiddenDateUpdatesIrisWithoutRegeneratingStudentVersion() {
        var exercise = exercise(ZonedDateTime.now().plusDays(7));
        var slide = slide(attachment(10L, 100L), ZonedDateTime.now().plusDays(1), false);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(slide));

        slideService.updateSlidesHiddenDate(exercise);

        verify(visibilitySyncService).markVisibilityDirty(Set.of(100L));
        verify(attachmentService, never()).regenerateStudentVersionOrRemoveOutdated(any());
    }

    @Test
    void supersededSlideIsUpdatedButAffectsNeitherIrisNorStudentVersion() {
        var exercise = exercise(ZonedDateTime.now().plusDays(7));
        var currentAttachment = attachment(10L, 100L);
        var currentSlide = slide(currentAttachment, null, false);
        var supersededSlide = slide(attachment(11L, 101L), null, true);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(currentSlide, supersededSlide));

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(supersededSlide.getHidden()).isNotNull();
        verify(visibilitySyncService).markVisibilityDirty(Set.of(100L));
        verify(attachmentService).regenerateStudentVersionOrRemoveOutdated(currentAttachment);
        verify(attachmentService, never()).regenerateStudentVersionOrRemoveOutdated(supersededSlide.getAttachmentVideoUnit().getAttachment());
    }

    @Test
    void severalSlidesOfOneAttachmentRegenerateItOnce() {
        var exercise = exercise(ZonedDateTime.now().plusDays(7));
        var attachment = attachment(10L, 100L);
        var firstSlide = slide(attachment, null, false);
        var secondSlide = slide(attachment, null, false);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(firstSlide, secondSlide));

        slideService.updateSlidesHiddenDate(exercise);

        verify(visibilitySyncService).markVisibilityDirty(Set.of(100L));
        verify(attachmentService).regenerateStudentVersionOrRemoveOutdated(attachment);
    }

    @Test
    void unchangedDueDateDoesNothing() {
        var dueDate = ZonedDateTime.now().plusDays(7);

        slideService.handleDueDateChange(dueDate, exercise(dueDate));

        verifyNoInteractions(slideRepository, slideUnhideService, visibilitySyncService, attachmentService);
    }

    private static TextExercise exercise(ZonedDateTime dueDate) {
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setDueDate(dueDate);
        return exercise;
    }

    private static Attachment attachment(long attachmentId, long unitId) {
        var unit = new AttachmentVideoUnit();
        unit.setId(unitId);
        var attachment = new Attachment();
        attachment.setId(attachmentId);
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);
        return attachment;
    }

    private static Slide slide(Attachment attachment, ZonedDateTime hidden, boolean superseded) {
        var slide = new Slide();
        slide.setAttachmentVideoUnit(attachment.getAttachmentVideoUnit());
        slide.setHidden(hidden);
        slide.setSuperseded(superseded);
        return slide;
    }
}
