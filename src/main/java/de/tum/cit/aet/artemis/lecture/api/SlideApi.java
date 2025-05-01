package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

    public Slide findSlideByAttachmentUnitIdAndSlideNumber(Long attachmentUnitId, Integer slideNumber) {
        return slideRepository.findSlideByAttachmentUnitIdAndSlideNumber(attachmentUnitId, slideNumber);
    }

    public void handleDueDateChange(Exercise originalExercise, Exercise updatedExercise) {
        slideService.handleDueDateChange(originalExercise, updatedExercise);
    }
}
