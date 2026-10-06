package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
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

    private final AttachmentService attachmentService;

    private final LectureUnitVisibilitySyncService lectureUnitVisibilitySyncService;

    public SlideService(SlideRepository slideRepository, SlideUnhideService slideUnhideService, AttachmentService attachmentService,
            LectureUnitVisibilitySyncService lectureUnitVisibilitySyncService) {
        this.slideRepository = slideRepository;
        this.slideUnhideService = slideUnhideService;
        this.attachmentService = attachmentService;
        this.lectureUnitVisibilitySyncService = lectureUnitVisibilitySyncService;
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
        if (!Objects.equals(originalDueDate, updatedExercise.getDueDate())) {
            updateSlidesHiddenDate(updatedExercise);
        }
    }

    /**
     * Updates the hidden date of slides associated with the given exercise to match the exercise's due date.
     * A due date in the future hides the slides until then; no due date, or one that has passed, makes them visible.
     * <p>
     * Afterwards, Iris is told about the new visibility of every affected unit, and the student version is regenerated for each attachment whose set of visible slides
     * changed. Both run after the slides are saved and neither can make this method fail, so the exercise update that called it always completes.
     *
     * @param exercise The exercise whose due date has changed
     */
    public void updateSlidesHiddenDate(Exercise exercise) {
        List<Slide> relatedSlides = slideRepository.findByExerciseId(exercise.getId());
        if (relatedSlides.isEmpty()) {
            return;
        }

        log.debug("Updating hidden date for {} slides related to exercise {}", relatedSlides.size(), exercise.getId());

        ZonedDateTime dueDate = exercise.getDueDate();
        ZonedDateTime newHiddenDate = dueDate != null && dueDate.isAfter(ZonedDateTime.now()) ? dueDate : null;

        // Superseded slides belong to an earlier file of their unit. Their hidden date is updated like before, but they neither affect what students see nor what Iris knows.
        Set<Long> affectedUnitIds = new LinkedHashSet<>();
        Map<Long, Attachment> attachmentsWithChangedVisibleSlides = new LinkedHashMap<>();
        relatedSlides.stream().filter(slide -> !slide.isSuperseded()).forEach(slide -> {
            AttachmentVideoUnit unit = slide.getAttachmentVideoUnit();
            affectedUnitIds.add(unit.getId());
            boolean visibilityChanges = (slide.getHidden() == null) != (newHiddenDate == null);
            Attachment attachment = unit.getAttachment();
            if (visibilityChanges && attachment != null) {
                attachmentsWithChangedVisibleSlides.putIfAbsent(attachment.getId(), attachment);
            }
        });

        relatedSlides.forEach(slide -> slide.setHidden(newHiddenDate));
        slideRepository.saveAll(relatedSlides);
        relatedSlides.forEach(slideUnhideService::handleSlideHiddenUpdate);

        lectureUnitVisibilitySyncService.markVisibilityDirty(affectedUnitIds);
        attachmentsWithChangedVisibleSlides.values().forEach(attachmentService::regenerateStudentVersionOrRemoveOutdated);
    }
}
