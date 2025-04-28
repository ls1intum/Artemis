package de.tum.cit.aet.artemis.lecture.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.lecture.service.SlideService;

/**
 * API for managing slides.
 */
@Conditional(LectureEnabled.class)
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
