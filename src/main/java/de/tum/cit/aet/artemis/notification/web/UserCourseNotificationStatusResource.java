package de.tum.cit.aet.artemis.notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSeenRequestDTO;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationStatusUpdateRequestDTO;
import de.tum.cit.aet.artemis.notification.service.UserCourseNotificationStatusService;

@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.COURSE_NOTIFICATIONS)
@RestController
@RequestMapping("api/notification/courses/")
public class UserCourseNotificationStatusResource {

    private static final Logger log = LoggerFactory.getLogger(UserCourseNotificationStatusResource.class);

    private final UserCourseNotificationStatusService userCourseNotificationStatusService;

    private final UserRepository userRepository;

    public UserCourseNotificationStatusResource(UserCourseNotificationStatusService userCourseNotificationStatusService, UserRepository userRepository) {
        this.userCourseNotificationStatusService = userCourseNotificationStatusService;
        this.userRepository = userRepository;
    }

    /**
     * PUT communication/notification/{courseId}/status : Update status of multiple notifications for the current user
     * <p>
     * The web client sends this when the user acts on notifications: closing one, or marking them all as read. It marks
     * notifications as seen merely because they were shown through {@link #markDisplayedNotificationsAsSeen} instead, so
     * that the feature usage report can tell the two apart.
     *
     * @param courseId   the ID of the course
     * @param requestDTO the request containing the list of notification ids as well as the status
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @PutMapping("{courseId}/status")
    public ResponseEntity<Void> updateNotificationStatus(@PathVariable Long courseId, @RequestBody UserCourseNotificationStatusUpdateRequestDTO requestDTO) {
        log.debug("REST request to update notification status to {} for notifications {} in course {}", requestDTO.statusType(), requestDTO.notificationIds(), courseId);

        var currentUser = userRepository.getUser();

        userCourseNotificationStatusService.updateUserCourseNotificationStatus(currentUser, requestDTO.notificationIds(), requestDTO.statusType(), courseId);

        return ResponseEntity.ok().build();
    }

    /**
     * PUT communication/notification/{courseId}/seen : Mark notifications as seen because the client displayed them
     * <p>
     * The client calls this on its own whenever the open notification overview shows unseen notifications, so a call says
     * nothing about anyone acting on them: opening the overview is already counted by the request that loads it.
     *
     * @param courseId   the ID of the course
     * @param requestDTO the ids of the notifications the client displayed
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @PutMapping("{courseId}/seen")
    public ResponseEntity<Void> markDisplayedNotificationsAsSeen(@PathVariable Long courseId, @RequestBody UserCourseNotificationSeenRequestDTO requestDTO) {
        log.debug("REST request to mark the displayed notifications {} in course {} as seen", requestDTO.notificationIds(), courseId);

        var currentUser = userRepository.getUser();

        userCourseNotificationStatusService.updateUserCourseNotificationStatus(currentUser, requestDTO.notificationIds(), UserCourseNotificationStatusType.SEEN, courseId);

        return ResponseEntity.ok().build();
    }

    /**
     * PUT communication/notification/{courseId}/archive-all : Update status of all notifications for the course to archived
     *
     * @param courseId the ID of the course
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastStudentInCourse
    @PutMapping("{courseId}/archive-all")
    public ResponseEntity<Void> archiveAll(@PathVariable Long courseId) {
        log.debug("REST request to update notification status to ARCHIVED for all notifications in course {}", courseId);

        var currentUser = userRepository.getUser();

        userCourseNotificationStatusService.archiveUserCourseNotificationStatus(courseId, currentUser.getId());

        return ResponseEntity.ok().build();
    }
}
