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

import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class SlideServiceTest {

    @Test
    void updateSlidesHiddenDateCallsVisibilitySyncAfterSlidesAreSavedAndUnhideJobsScheduled() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService);
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
    }

    @Test
    void updateSlidesHiddenDateDoesNotMarkVisibilityDirtyWithoutRelatedSlides() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService);
        var exercise = exerciseWithDueDate();
        when(slideRepository.findByExerciseId(exercise.getId())).thenReturn(List.of());

        slideService.updateSlidesHiddenDate(exercise);

        verify(slideRepository, never()).saveAll(any());
        verifyNoInteractions(slideUnhideService, visibilitySyncService);
    }

    @Test
    void updateSlidesHiddenDateDoesNotMarkVisibilityDirtyWhenDueDateIsNull() {
        var slideRepository = mock(SlideRepository.class);
        var slideUnhideService = mock(SlideUnhideService.class);
        var visibilitySyncService = mock(LectureUnitVisibilitySyncService.class);
        var slideService = new SlideService(slideRepository, slideUnhideService, visibilitySyncService);
        var exercise = new TextExercise();
        exercise.setId(42L);

        slideService.updateSlidesHiddenDate(exercise);

        verifyNoInteractions(slideRepository, slideUnhideService, visibilitySyncService);
    }

    private static TextExercise exerciseWithDueDate() {
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setDueDate(ZonedDateTime.parse("2026-07-03T12:00:00Z"));
        return exercise;
    }
}
