package de.tum.cit.aet.artemis.course_notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.course_notification.dto.UserCourseNotificationStatusUpdateRequest;
import de.tum.cit.aet.artemis.course_notification.service.UserCourseNotificationStatusService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class UserCourseNotificationStatusResource {

    private static final Logger log = LoggerFactory.getLogger(UserCourseNotificationStatusResource.class);

    private final UserCourseNotificationStatusService userCourseNotificationStatusService;

    private final UserRepository userRepository;

    public UserCourseNotificationStatusResource(UserCourseNotificationStatusService userCourseNotificationStatusService, UserRepository userRepository) {
        this.userCourseNotificationStatusService = userCourseNotificationStatusService;
        this.userRepository = userRepository;
    }

    /**
     * PUT /courses/{courseId}/notifications/status : Update status of multiple notifications for the current user
     *
     * @param courseId   the ID of the course
     * @param requestDTO the request containing the list of notification ids as well as the status
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("/courses/{courseId}/notifications/status")
    public ResponseEntity<Void> updateNotificationStatus(@PathVariable Long courseId, @RequestBody UserCourseNotificationStatusUpdateRequest requestDTO) {
        log.debug("REST request to update notification status to {} for notifications {} in course {}", requestDTO.statusType(), requestDTO.notificationIds(), courseId);

        var currentUser = userRepository.getUser();

        userCourseNotificationStatusService.updateUserCourseNotificationStatus(currentUser, requestDTO.notificationIds(), requestDTO.statusType(), courseId);

        return ResponseEntity.ok().build();
    }
}
