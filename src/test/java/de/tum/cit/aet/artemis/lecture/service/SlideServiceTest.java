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

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class SlideServiceTest {

    @Test
    void updateSlidesHiddenDateCallsVisibilitySyncAfterSlidesAreSavedAndUnhideJobsScheduled() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService);
        var exercise = exerciseWithDueDate();
        var firstSlide = new Slide();
        firstSlide.setId(1L);
        var secondSlide = new Slide();
        secondSlide.setId(2L);
        var relatedSlides = List.of(firstSlide, secondSlide);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(relatedSlides);

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(firstSlide.getHidden()).isEqualTo(exercise.getDueDate());
        assertThat(secondSlide.getHidden()).isEqualTo(exercise.getDueDate());

        var inOrder = inOrder(slideRepository, slideUnhideService, visibilitySyncService);
        inOrder.verify(slideRepository).saveAll(relatedSlides);
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(firstSlide);
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(secondSlide);
        inOrder.verify(visibilitySyncService).markVisibilityDirtyForExercise(exercise);
        verifyNoInteractions(attachmentService);
    }

    @Test
    void updateSlidesHiddenDateDoesNotMarkVisibilityDirtyWithoutRelatedSlides() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService);
        var exercise = exerciseWithDueDate();
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of());

        slideService.updateSlidesHiddenDate(exercise);

        verify(slideRepository, never()).saveAll(any());
        verifyNoInteractions(slideUnhideService, visibilitySyncService, attachmentService);
    }

    @Test
    void updateSlidesHiddenDateClearsHiddenDateAndMarksVisibilityDirtyWhenDueDateIsNull() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService);
        var exercise = new TextExercise();
        exercise.setId(42L);
        var attachment = new Attachment();
        var attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setAttachment(attachment);
        var slide = new Slide();
        slide.setHidden(ZonedDateTime.parse("2026-07-03T12:00:00Z"));
        slide.setAttachmentVideoUnit(attachmentVideoUnit);
        var secondSlide = new Slide();
        secondSlide.setHidden(ZonedDateTime.parse("2026-07-03T12:00:00Z"));
        secondSlide.setAttachmentVideoUnit(attachmentVideoUnit);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(slide, secondSlide));

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(slide.getHidden()).isNull();
        var inOrder = inOrder(slideRepository, slideUnhideService, visibilitySyncService);
        inOrder.verify(slideRepository).saveAll(List.of(slide, secondSlide));
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(slide);
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(secondSlide);
        verify(attachmentService).regenerateStudentVersion(attachment);
        inOrder.verify(visibilitySyncService).markVisibilityDirtyForExercise(exercise);
    }

    private static TextExercise exerciseWithDueDate() {
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setDueDate(ZonedDateTime.parse("2026-07-03T12:00:00Z"));
        return exercise;
    }
}
