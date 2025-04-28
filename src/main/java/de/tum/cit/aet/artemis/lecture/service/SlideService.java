package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Conditional(LectureEnabled.class)
@Service
public class SlideService {

    private static final Logger log = LoggerFactory.getLogger(SlideService.class);

    private final SlideRepository slideRepository;

    private final SlideUnhideService slideUnhideService;

    public SlideService(SlideRepository slideRepository, SlideUnhideService slideUnhideService) {
        this.slideRepository = slideRepository;
        this.slideUnhideService = slideUnhideService;
    }

    /**
     * Checks if the due date of an exercise has changed and updates related slides if needed.
     * This method should be called after saving an updated exercise.
     *
     * @param originalExercise The original exercise before the update
     * @param updatedExercise  The updated exercise after the update
     */
    public void handleDueDateChange(Exercise originalExercise, Exercise updatedExercise) {
        ZonedDateTime originalDueDate = originalExercise.getDueDate();
        ZonedDateTime updatedDueDate = updatedExercise.getDueDate();

        // Check if the due date has changed
        if (updatedDueDate != null && (originalDueDate == null || !originalDueDate.equals(updatedDueDate))) {

            updateSlidesHiddenDate(updatedExercise);
        }

    }

    /**
     * Updates the hidden date of slides associated with the given exercise to match the exercise's due date.
     * This method should only be called when an exercise's due date has changed.
     *
     * @param exercise The exercise whose due date has changed
     */
    public void updateSlidesHiddenDate(Exercise exercise) {
        if (exercise.getDueDate() == null) {
            return;
        }

        List<Slide> relatedSlides = slideRepository.findByExerciseId(exercise.getId());
        if (relatedSlides.isEmpty()) {
            return;
        }

        log.debug("Updating hidden date for {} slides related to exercise {}", relatedSlides.size(), exercise.getId());

        ZonedDateTime newHiddenDate = exercise.getDueDate();

        relatedSlides.forEach(slide -> slide.setHidden(newHiddenDate));
        slideRepository.saveAll(relatedSlides);
        relatedSlides.forEach(slideUnhideService::handleSlideHiddenUpdate);
    }
}
