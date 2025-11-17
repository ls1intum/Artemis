package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.lecture.service.SlideService;

/**
 * API for managing slides.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class SlideApi extends AbstractLectureApi {

    private final SlideService slideService;

    private final SlideRepository slideRepository;

    public SlideApi(SlideService slideService, SlideRepository slideRepository) {
        this.slideService = slideService;
        this.slideRepository = slideRepository;
    }

    public Slide findSlideByIdElseThrow(long id) {
        return slideRepository.findByIdElseThrow(id);
    }

    public Slide findSlideByAttachmentVideoUnitIdAndSlideNumber(long attachmentVideoUnitId, int slideNumber) {
        return slideRepository.findSlideByAttachmentVideoUnitIdAndSlideNumber(attachmentVideoUnitId, slideNumber);
    }

    public void handleDueDateChange(Exercise originalExercise, Exercise updatedExercise) {
        slideService.handleDueDateChange(originalExercise, updatedExercise);
    }

    public void handleDueDateChangeWithDate(ZonedDateTime originalDueDate, Exercise updatedExercise) {
        slideService.handleDueDateChangeWithDate(originalDueDate, updatedExercise);
    }
}
