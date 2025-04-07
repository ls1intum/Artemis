package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationInfoDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationPageableDTO;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationRegistryService;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationSettingPresetRegistryService;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

@Profile(PROFILE_CORE)
@RestController
@FeatureToggle(Feature.CourseSpecificNotifications)
@RequestMapping("api/communication/")
public class CourseNotificationResource {

    private final CourseNotificationService courseNotificationService;

    private final CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    private final CourseNotificationRegistryService courseNotificationRegistryService;

    private final UserRepository userRepository;

    public CourseNotificationResource(CourseNotificationService courseNotificationService,
            CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService, CourseNotificationRegistryService courseNotificationRegistryService,
            UserRepository userRepository) {
        this.courseNotificationService = courseNotificationService;
        this.courseNotificationSettingPresetRegistryService = courseNotificationSettingPresetRegistryService;
        this.courseNotificationRegistryService = courseNotificationRegistryService;
        this.userRepository = userRepository;
    }

    /**
     * GET communication/notification/{courseId}: get all non-archived course notifications for the current user
     *
     * @param courseId the ID of the course
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of course notifications in body
     */
    // TODO: not a good REST URL design, consider changing courseId to a QueryParam or put it into the front of the URL: courses/{courseId}/notifications
    @EnforceAtLeastStudentInCourse
    @GetMapping("notification/{courseId}")
    public ResponseEntity<CourseNotificationPageableDTO<CourseNotificationDTO>> getCourseNotifications(@PathVariable Long courseId, Pageable pageable) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(courseNotificationService.getCourseNotifications(pageable, courseId, userRepository.getUser().getId()));
    }

    /**
     * GET communication/notification/info: get all notification types and presets
     *
     * @return the ResponseEntity with status 200 (OK) and the list of all notification types and presets
     */
    @EnforceAtLeastStudent
    @GetMapping("notification/info")
    public ResponseEntity<CourseNotificationInfoDTO> getCourseNotificationInfo() {
        var presetDTOs = courseNotificationSettingPresetRegistryService.getSettingPresetDTOs();
        var notificationTypes = courseNotificationRegistryService.getNotificationTypes();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new CourseNotificationInfoDTO(notificationTypes, NotificationChannelOption.values(), presetDTOs));
    }
}
