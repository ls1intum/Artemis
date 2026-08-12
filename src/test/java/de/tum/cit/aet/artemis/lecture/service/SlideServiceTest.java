package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService, new TransactionAfterCommitService());
        var exercise = exerciseWithDueDate();
        var firstSlide = new Slide();
        firstSlide.setId(1L);
        var secondSlide = new Slide();
        secondSlide.setId(2L);
        var attachment = new Attachment();
        var attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setAttachment(attachment);
        firstSlide.setAttachmentVideoUnit(attachmentVideoUnit);
        secondSlide.setAttachmentVideoUnit(attachmentVideoUnit);
        var relatedSlides = List.of(firstSlide, secondSlide);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(relatedSlides);

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(firstSlide.getHidden()).isEqualTo(exercise.getDueDate());
        assertThat(secondSlide.getHidden()).isEqualTo(exercise.getDueDate());

        var inOrder = inOrder(slideRepository, slideUnhideService, visibilitySyncService, attachmentService);
        inOrder.verify(visibilitySyncService).lockAffectedAttachmentVideoUnits(relatedSlides);
        inOrder.verify(slideRepository).saveAll(relatedSlides);
        inOrder.verify(attachmentService).markStudentVersionRegenerationPending(attachment);
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(firstSlide);
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(secondSlide);
        inOrder.verify(visibilitySyncService).markVisibilityDirtyForSlides(relatedSlides);
        inOrder.verify(attachmentService).regenerateStudentVersionOrLeavePending(attachment);
    }

    @Test
    void updateSlidesHiddenDateDoesNotMarkVisibilityDirtyWithoutRelatedSlides() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService, new TransactionAfterCommitService());
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
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService, new TransactionAfterCommitService());
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
        inOrder.verify(visibilitySyncService).lockAffectedAttachmentVideoUnits(List.of(slide, secondSlide));
        inOrder.verify(slideRepository).saveAll(List.of(slide, secondSlide));
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(slide);
        inOrder.verify(slideUnhideService).handleSlideHiddenUpdate(secondSlide);
        verify(attachmentService).regenerateStudentVersionOrLeavePending(attachment);
        verify(attachmentService).markStudentVersionRegenerationPending(attachment);
        inOrder.verify(visibilitySyncService).markVisibilityDirtyForSlides(List.of(slide, secondSlide));
    }

    @Test
    void updateSlidesHiddenDateRegeneratesSharedAttachmentOnlyOnce() {
        // Guard the id-based attachment deduplication defensively, even though the current mapping does not permit two units to share an attachment.
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService, new TransactionAfterCommitService());
        var exercise = exerciseWithDueDate();
        var attachment = new Attachment();
        attachment.setId(7L);
        var firstUnit = new AttachmentVideoUnit();
        firstUnit.setAttachment(attachment);
        var secondUnit = new AttachmentVideoUnit();
        secondUnit.setAttachment(attachment);
        var firstSlide = new Slide();
        firstSlide.setAttachmentVideoUnit(firstUnit);
        var secondSlide = new Slide();
        secondSlide.setAttachmentVideoUnit(secondUnit);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(firstSlide, secondSlide));

        slideService.updateSlidesHiddenDate(exercise);

        verify(attachmentService).regenerateStudentVersionOrLeavePending(attachment);
        verify(attachmentService).markStudentVersionRegenerationPending(attachment);
    }

    @Test
    void updateSlidesHiddenDatePropagatesVisibilityPersistenceFailure() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService, new TransactionAfterCommitService());
        var exercise = exerciseWithDueDate();
        var attachment = new Attachment();
        attachment.setId(7L);
        var unit = new AttachmentVideoUnit();
        unit.setAttachment(attachment);
        var slide = new Slide();
        slide.setAttachmentVideoUnit(unit);
        var relatedSlides = List.of(slide);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(relatedSlides);
        doThrow(new IllegalStateException("sync failed")).when(visibilitySyncService).markVisibilityDirtyForSlides(relatedSlides);
        assertThatThrownBy(() -> slideService.updateSlidesHiddenDate(exercise)).isInstanceOf(IllegalStateException.class).hasMessage("sync failed");

        verify(slideRepository).saveAll(relatedSlides);
        verify(visibilitySyncService).markVisibilityDirtyForSlides(relatedSlides);
        verify(attachmentService, never()).regenerateStudentVersionOrLeavePending(attachment);
        verify(attachmentService).markStudentVersionRegenerationPending(attachment);
    }

    @Test
    void changingFutureHiddenDateKeepsExistingStudentVersion() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService, new TransactionAfterCommitService());
        var exercise = exerciseWithDueDate();
        var attachment = new Attachment();
        var attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setAttachment(attachment);
        var slide = new Slide();
        slide.setHidden(ZonedDateTime.now().plusDays(2));
        slide.setAttachmentVideoUnit(attachmentVideoUnit);
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(slide));

        slideService.updateSlidesHiddenDate(exercise);

        assertThat(slide.getHidden()).isEqualTo(exercise.getDueDate());
        verify(attachmentService, never()).markStudentVersionRegenerationPending(any());
        verify(attachmentService, never()).regenerateStudentVersionOrLeavePending(any());
        verify(slideUnhideService).handleSlideHiddenUpdate(slide);
        verify(visibilitySyncService).markVisibilityDirtyForSlides(List.of(slide));
    }

    @Test
    void updateSlidesHiddenDateDefersUnhideSchedulingUntilAfterCommit() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var attachmentService = mock(AttachmentService.class);
        var transactionAfterCommitService = mock(TransactionAfterCommitService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService, attachmentService, transactionAfterCommitService);
        var exercise = exerciseWithDueDate();
        var slide = new Slide();
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of(slide));

        slideService.updateSlidesHiddenDate(exercise);

        verifyNoInteractions(slideUnhideService);
        var callback = ArgumentCaptor.forClass(Runnable.class);
        verify(transactionAfterCommitService).execute(callback.capture());
        callback.getValue().run();
        verify(slideUnhideService).handleSlideHiddenUpdate(slide);
    }

    private static TextExercise exerciseWithDueDate() {
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setDueDate(ZonedDateTime.now().plusDays(7));
        return exercise;
    }
}
