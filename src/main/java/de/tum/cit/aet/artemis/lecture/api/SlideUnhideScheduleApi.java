package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.service.SlideUnhideScheduleService;

/**
 * API for slide unhide scheduling operations.
 */
@Conditional(LectureEnabled.class)
@Profile(PROFILE_CORE_AND_SCHEDULING)
@Controller
@Lazy
public class SlideUnhideScheduleApi extends AbstractLectureApi {

    private final SlideUnhideScheduleService slideUnhideScheduleService;

    public SlideUnhideScheduleApi(SlideUnhideScheduleService slideUnhideScheduleService) {
        this.slideUnhideScheduleService = slideUnhideScheduleService;
    }

    /**
     * Schedules a slide for unhiding at its configured time.
     *
     * @param slideId the ID of the slide to schedule for unhiding
     */
    public void scheduleSlideUnhiding(Long slideId) {
        slideUnhideScheduleService.scheduleSlideUnhiding(slideId);
    }

    /**
     * Cancels a scheduled unhiding task for a slide.
     *
     * @param slideId the ID of the slide whose scheduled unhiding should be canceled
     */
    public void cancelScheduledUnhiding(Long slideId) {
        slideUnhideScheduleService.cancelScheduledUnhiding(slideId);
    }
}
