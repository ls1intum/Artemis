package de.tum.cit.aet.artemis.notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
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
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationInfoDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationPageDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationRegistryService;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationSettingPresetRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.COURSE_NOTIFICATIONS)
@RestController
@RequestMapping("api/notification/courses/")
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
     * GET /api/notification/courses/{courseId}: get all non-archived course notifications for the current user
     *
     * @param courseId the ID of the course
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of course notifications in body
     */
    @EnforceAtLeastStudentInCourse
    @Operation(operationId = "getCourseNotifications")
    @ApiResponse(responseCode = "200", description = "Non-archived notifications, with nullable reconstruction entries and unchanged pagination totals")
    @GetMapping("{courseId}")
    public ResponseEntity<CourseNotificationPageDTO> getCourseNotifications(@PathVariable Long courseId, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(CourseNotificationPageDTO.from(courseNotificationService.getCourseNotifications(pageable, courseId, userRepository.getUser().getId())));
    }

    /**
     * GET /api/notification/courses/info: get all notification types and presets
     *
     * @return the ResponseEntity with status 200 (OK) and the list of all notification types and presets
     */
    @EnforceAtLeastStudent
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @Operation(operationId = "getCourseNotificationInfo")
    @ApiResponse(responseCode = "200", description = "Notification types, delivery channels and setting presets")
    @GetMapping("info")
    public ResponseEntity<CourseNotificationInfoDTO> getCourseNotificationInfo() {
        var presetDTOs = courseNotificationSettingPresetRegistryService.getSettingPresetDTOs();
        var notificationTypes = courseNotificationRegistryService.getNotificationTypes();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(new CourseNotificationInfoDTO(notificationTypes, List.of(NotificationChannelOption.values()), presetDTOs));
    }
}
