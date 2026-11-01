package de.tum.cit.aet.artemis.notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationSettingInfoDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationSettingSpecificationRequestDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationSettingPresetRegistryService;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.NOTIFICATION_SETTINGS)
@RestController
@RequestMapping("api/notification/courses/")
public class UserCourseNotificationSettingResource {

    private static final Logger log = LoggerFactory.getLogger(UserCourseNotificationSettingResource.class);

    private final CourseNotificationSettingService courseNotificationSettingService;

    private final CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    private final UserRepository userRepository;

    public UserCourseNotificationSettingResource(CourseNotificationSettingService courseNotificationSettingService,
            CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService, UserRepository userRepository) {
        this.courseNotificationSettingService = courseNotificationSettingService;
        this.courseNotificationSettingPresetRegistryService = courseNotificationSettingPresetRegistryService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/notification/courses/{courseId}/settings: Get setting information for the current user
     *
     * @param courseId the ID of the course
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @Operation(operationId = "getUserCourseNotificationSettings")
    @ApiResponse(responseCode = "200", description = "Effective notification settings for the current course member")
    @GetMapping("{courseId}/settings")
    public ResponseEntity<CourseNotificationSettingInfoDTO> getSettingInfo(@PathVariable Long courseId) {
        log.debug("REST request to get notification setting info for course {}", courseId);

        var user = userRepository.getUser();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(courseNotificationSettingService.getSettingInfo(user.getId(), user.isTestUser(), courseId));
    }

    /**
     * PUT /api/notification/courses/{courseId}/setting-preset: Set a specified setting preset for the current user
     *
     * @param courseId the ID of the course
     * @param presetId the ID of the preset
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @Operation(operationId = "setUserCourseNotificationPreset", description = "Apply the JSON numeric preset id for the current course member, including custom preset 0")
    @ApiResponse(responseCode = "200", description = "Preset applied; empty response body", content = @Content)
    @ApiResponse(responseCode = "400", description = "Missing or unknown preset id", content = @Content(mediaType = "application/problem+json", schema = @Schema(type = "object")))
    @PutMapping("{courseId}/setting-preset")
    public ResponseEntity<Void> setSettingPreset(@PathVariable Long courseId,
            @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(type = "integer", format = "int32", minimum = "-32768", maximum = "32767"))) Short presetId) {
        log.debug("REST request to update notification setting preset to {} in course {}", presetId, courseId);

        if (presetId == null || !courseNotificationSettingPresetRegistryService.isValidSettingPreset(presetId.intValue())) {
            throw new BadRequestAlertException("The provided preset could not be found.", "notification", "presetDoesNotExist");
        }

        var user = userRepository.getUser();

        courseNotificationSettingService.applyPreset(presetId, user.getId(), user.isTestUser(), courseId);

        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/notification/courses/{courseId}/setting-specification: Set a set of setting specifications for the current user
     *
     * @param courseId                   the ID of the course
     * @param notificationSpecifications the map of notification types and their corresponding setting specifications
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @Operation(operationId = "setUserCourseNotificationSpecification", description = "Apply partial channel overrides for the current course member and select custom preset 0")
    @ApiResponse(responseCode = "200", description = "Specifications applied; empty response body", content = @Content)
    @PutMapping("{courseId}/setting-specification")
    public ResponseEntity<Void> setSettingSpecification(@PathVariable Long courseId, @RequestBody CourseNotificationSettingSpecificationRequestDTO notificationSpecifications) {
        log.debug("REST request to update notification setting specification in course {}", courseId);

        var user = userRepository.getUser();

        courseNotificationSettingService.applySpecification(notificationSpecifications.notificationTypeChannels(), user.getId(), user.isTestUser(), courseId);

        return ResponseEntity.ok().build();
    }
}
