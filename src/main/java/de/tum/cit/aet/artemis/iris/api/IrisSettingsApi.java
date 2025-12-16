package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseSettingsWithRateLimitDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

@Profile(PROFILE_IRIS)
@Controller
@Lazy
public class IrisSettingsApi extends AbstractIrisApi {

    private final IrisSettingsService irisSettingsService;

    public IrisSettingsApi(IrisSettingsService irisSettingsService) {
        this.irisSettingsService = irisSettingsService;
    }

    public IrisCourseSettingsWithRateLimitDTO getSettingsForCourse(long courseId) {
        return irisSettingsService.getCourseSettingsWithRateLimit(courseId);
    }

    public boolean isIrisEnabledForCourse(long courseId) {
        return irisSettingsService.isEnabledForCourse(courseId);
    }

    public void deleteSettingsFor(Course course) {
        irisSettingsService.deleteSettingsFor(course);
    }
}
