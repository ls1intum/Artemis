package de.tum.cit.aet.artemis.notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.notification.config.NotificationLegacyRestPaths;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationInfoDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationPageableDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationRegistryService;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationSettingPresetRegistryService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
// The legacy "api/communication/" prefix is kept for backwards compatibility with deployed clients and will be removed
// once those clients have migrated. New clients should use the "api/notification/" prefix.
@SuppressWarnings("deprecation")
@RequestMapping({ "api/notification/courses/", NotificationLegacyRestPaths.COMMUNICATION_NOTIFICATION_PREFIX })
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
    @EnforceAtLeastStudentInCourse
    @GetMapping("{courseId}")
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
    @GetMapping("info")
    public ResponseEntity<CourseNotificationInfoDTO> getCourseNotificationInfo() {
        var presetDTOs = courseNotificationSettingPresetRegistryService.getSettingPresetDTOs();
        var notificationTypes = courseNotificationRegistryService.getNotificationTypes();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new CourseNotificationInfoDTO(notificationTypes, NotificationChannelOption.values(), presetDTOs));
    }
}
