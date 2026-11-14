package de.tum.cit.aet.artemis.iris.web;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseSettingsWithRateLimitDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Conditional(IrisEnabled.class)
@Lazy
@FeatureUsage(UserFeature.IRIS_SETTINGS)
@RestController
@RequestMapping("api/iris/")
public class IrisSettingsResource {

    private final CourseRepository courseRepository;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authorizationCheckService;

    public IrisSettingsResource(CourseRepository courseRepository, IrisSettingsService irisSettingsService, AuthorizationCheckService authorizationCheckService) {
        this.courseRepository = courseRepository;
        this.irisSettingsService = irisSettingsService;
        this.authorizationCheckService = authorizationCheckService;
    }

    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("courses/{courseId}/iris-settings")
    @EnforceAtLeastStudentInCourse
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<IrisCourseSettingsWithRateLimitDTO> getCourseSettings(@PathVariable Long courseId) {
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(irisSettingsService.getCourseSettingsWithRateLimit(courseId));
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
    @ApiResponse(responseCode = "200", description = "Course Iris settings updated", headers = @Header(name = IrisOpenApiHeaders.FRAME_OPTIONS, description = IrisOpenApiHeaders.FRAME_OPTIONS_DESCRIPTION, schema = @Schema(type = "string")))
    public ResponseEntity<IrisCourseSettingsWithRateLimitDTO> updateCourseSettings(@PathVariable Long courseId, @Valid @RequestBody IrisCourseSettingsDTO update) {
        courseRepository.findByIdElseThrow(courseId);
        var isAdmin = authorizationCheckService.isCurrentUserAdminAccessEnabled();
        var saved = irisSettingsService.updateCourseSettings(courseId, update.toDomain(), isAdmin);
        return ResponseEntity.ok(saved);
    }
}
