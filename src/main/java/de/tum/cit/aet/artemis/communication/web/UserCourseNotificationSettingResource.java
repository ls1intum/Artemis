package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSettingInfoDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSettingSpecificationRequestDTO;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationSettingPresetRegistryService;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationSettingService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

@Profile(PROFILE_CORE)
@RestController
@FeatureToggle(Feature.CourseSpecificNotifications)
@RequestMapping("api/communication/")
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
     * GET communication/notification/{courseId}/settings: Get setting information for the current user
     *
     * @param courseId the ID of the course
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @GetMapping("notification/{courseId}/settings")
    public ResponseEntity<CourseNotificationSettingInfoDTO> getSettingInfo(@PathVariable Long courseId) {
        log.debug("REST request to get notification setting info for course {}", courseId);

        var user = userRepository.getUser();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(courseNotificationSettingService.getSettingInfo(user.getId(), courseId));
    }

    /**
     * PUT communication/notification/{courseId}/setting-preset: Set a specified setting preset for the current user
     *
     * @param courseId the ID of the course
     * @param presetId the ID of the preset
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @PutMapping("notification/{courseId}/setting-preset")
    public ResponseEntity<Void> setSettingPreset(@PathVariable Long courseId, @RequestBody Short presetId) {
        log.debug("REST request to update notification setting preset to {} in course {}", presetId, courseId);

        if (presetId == null || !courseNotificationSettingPresetRegistryService.isValidSettingPreset(presetId.intValue())) {
            throw new BadRequestAlertException("The provided preset could not be found.", "notification", "presetDoesNotExist");
        }

        var user = userRepository.getUser();

        courseNotificationSettingService.applyPreset(presetId, user.getId(), courseId);

        return ResponseEntity.ok().build();
    }

    /**
     * PUT communication/notification/{courseId}/setting-specification: Set a set of setting specifications for the current user
     *
     * @param courseId                   the ID of the course
     * @param notificationSpecifications the map of notification types and their corresponding setting specifications
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @PutMapping("notification/{courseId}/setting-specification")
    public ResponseEntity<Void> setSettingSpecification(@PathVariable Long courseId, @RequestBody CourseNotificationSettingSpecificationRequestDTO notificationSpecifications) {
        log.debug("REST request to update notification setting specification in course {}", courseId);

        var user = userRepository.getUser();

        courseNotificationSettingService.applySpecification(notificationSpecifications.notificationTypeChannels(), user.getId(), courseId);

        return ResponseEntity.ok().build();
    }
}
