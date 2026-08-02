package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class SlideService {

    private static final Logger log = LoggerFactory.getLogger(SlideService.class);

    private final SlideRepository slideRepository;

    private final SlideUnhideService slideUnhideService;

    private final LectureUnitVisibilitySyncService lectureUnitVisibilitySyncService;

    private final AttachmentService attachmentService;

    public SlideService(SlideRepository slideRepository, SlideUnhideService slideUnhideService, LectureUnitVisibilitySyncService lectureUnitVisibilitySyncService,
            AttachmentService attachmentService) {
        this.slideRepository = slideRepository;
        this.slideUnhideService = slideUnhideService;
        this.lectureUnitVisibilitySyncService = lectureUnitVisibilitySyncService;
        this.attachmentService = attachmentService;
    }

    /**
     * Checks if the due date of an exercise has changed and updates related slides if needed.
     * This method should be called after saving an updated exercise.
     *
     * @param originalExercise The original exercise before the update
     * @param updatedExercise  The updated exercise after the update
     */
    public void handleDueDateChange(Exercise originalExercise, Exercise updatedExercise) {
        handleDueDateChange(originalExercise.getDueDate(), updatedExercise);
    }

    /**
     * Checks if the due date of an exercise has changed and updates related slides if needed.
     * This method should be called after saving an updated exercise.
     *
     * @param originalDueDate The original due date before the update
     * @param updatedExercise The updated exercise after the update
     */
    public void handleDueDateChange(ZonedDateTime originalDueDate, Exercise updatedExercise) {
        ZonedDateTime updatedDueDate = updatedExercise.getDueDate();
        boolean hasDueDateChanged = !Objects.equals(originalDueDate, updatedDueDate);

        // Check if the due date has changed
        if (hasDueDateChanged) {

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
        List<Slide> relatedSlides = slideRepository.findByExerciseId(exercise.getId());
        if (relatedSlides.isEmpty()) {
            return;
        }

        log.debug("Updating hidden date for {} slides related to exercise {}", relatedSlides.size(), exercise.getId());

        ZonedDateTime newHiddenDate = exercise.getDueDate();

        relatedSlides.forEach(slide -> slide.setHidden(newHiddenDate));
        slideRepository.saveAll(relatedSlides);
        relatedSlides.forEach(slideUnhideService::handleSlideHiddenUpdate);
        try {
            lectureUnitVisibilitySyncService.markVisibilityDirtyForSlides(relatedSlides);
        }
        catch (Exception e) {
            log.error("Failed to mark lecture unit visibility dirty after updating slides for exercise {}: {}", exercise.getId(), e.getMessage(), e);
        }
        relatedSlides.stream().map(Slide::getAttachmentVideoUnit).filter(Objects::nonNull).map(AttachmentVideoUnit::getAttachment).filter(Objects::nonNull).distinct()
                .forEach(attachment -> {
                    try {
                        attachmentService.regenerateStudentVersion(attachment);
                    }
                    catch (Exception e) {
                        log.error("Failed to regenerate student version for attachment {} after updating slides for exercise {}: {}", attachment.getId(), exercise.getId(),
                                e.getMessage(), e);
                    }
                });
    }
}
