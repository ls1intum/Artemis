package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.dto.CourseIrisSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisSettingsResource {

    private final CourseRepository courseRepository;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    public IrisSettingsResource(CourseRepository courseRepository, IrisSettingsService irisSettingsService, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.irisSettingsService = irisSettingsService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    @GetMapping("courses/{courseId}/iris-settings")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CourseIrisSettingsDTO> getCourseSettings(@PathVariable Long courseId) {
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(irisSettingsService.getCourseSettingsDTO(courseId));
    }

    /**
     * Updates the Iris settings for a course.
     *
     * @param courseId the course id
     * @param update   the new settings to apply
     * @return the updated settings
     */
    @PutMapping("courses/{courseId}/iris-settings")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CourseIrisSettingsDTO> updateCourseSettings(@PathVariable Long courseId, @Valid @RequestBody IrisCourseSettingsDTO update) {
        courseRepository.findByIdElseThrow(courseId);
        var current = irisSettingsService.getSettingsForCourse(courseId);
        var request = Objects.requireNonNullElse(update, current);
        var isAdmin = authorizationCheckService.isAdmin(userRepository.getUserWithGroupsAndAuthorities());
        if (!isAdmin) {
            enforceInstructorRestrictions(request, current);
        }
        var saved = irisSettingsService.updateCourseSettings(courseId, request);
        return ResponseEntity.ok(saved);
    }

    private void enforceInstructorRestrictions(IrisCourseSettingsDTO request, IrisCourseSettingsDTO current) {
        if (!Objects.equals(request.variant(), current.variant())) {
            throw new AccessForbiddenAlertException("Only administrators can change the Iris pipeline variant", "IrisSettings", "irisVariantRestricted");
        }

        if (!sameRateLimit(request.rateLimit(), current.rateLimit())) {
            throw new AccessForbiddenAlertException("Only administrators can change Iris rate limits", "IrisSettings", "irisRateLimitRestricted");
        }
    }

    private boolean sameRateLimit(IrisRateLimitConfiguration left, IrisRateLimitConfiguration right) {
        Integer leftRequests = normalizeRateLimitValue(left == null ? null : left.requests());
        Integer rightRequests = normalizeRateLimitValue(right == null ? null : right.requests());
        Integer leftTimeframe = normalizeRateLimitValue(left == null ? null : left.timeframeHours());
        Integer rightTimeframe = normalizeRateLimitValue(right == null ? null : right.timeframeHours());

        return Objects.equals(leftRequests, rightRequests) && Objects.equals(leftTimeframe, rightTimeframe);
    }

    private Integer normalizeRateLimitValue(Integer value) {
        if (value == null || value == 0) {
            return null;
        }
        return value;
    }
}
