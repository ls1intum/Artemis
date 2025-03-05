package de.tum.cit.aet.artemis.course_notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.course_notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.course_notification.dto.CourseNotificationPageableDTO;
import de.tum.cit.aet.artemis.course_notification.service.CourseNotificationService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/course-notification/")
public class CourseNotificationResource {

    private final CourseNotificationService courseNotificationService;

    private final UserRepository userRepository;

    public CourseNotificationResource(CourseNotificationService courseNotificationService, UserRepository userRepository) {
        this.courseNotificationService = courseNotificationService;
        this.userRepository = userRepository;
    }

    /**
     * GET course-notification/courses/{courseId}: get all non-archived course notifications for the current user
     *
     * @param courseId the ID of the course
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of course notifications in body
     */
    @EnforceAtLeastStudent
    @GetMapping("course/{courseId}")
    public ResponseEntity<CourseNotificationPageableDTO<CourseNotificationDTO>> getCourseNotifications(@PathVariable Long courseId, Pageable pageable) {
        return ResponseEntity.ok().body(courseNotificationService.getCourseNotifications(pageable, courseId, userRepository.getUser().getId()));
    }
}
