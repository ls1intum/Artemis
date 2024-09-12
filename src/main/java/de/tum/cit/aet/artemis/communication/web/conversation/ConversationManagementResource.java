package de.tum.cit.aet.artemis.communication.web.conversation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

public class ConversationManagementResource {

    final CourseRepository courseRepository;

    public ConversationManagementResource(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /**
     * Checks if messaging is enabled for the given course, otherwise throws a ResponseStatusException with status 403 (Forbidden)
     *
     * @param courseId the id of the course to check
     */
    void checkMessagingEnabledElseThrow(Long courseId) {
        if (!(courseRepository.isMessagingEnabled(courseId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }
    }

    /**
     * Checks if messaging is enabled for the given course, otherwise throws a ResponseStatusException with status 403 (Forbidden)
     * <p>
     * Note: Keep in sync with courseRepository.isMessagingEnabled(courseId)
     *
     * @param course the course to check
     */
    void checkMessagingEnabledElseThrow(Course course) {
        if (course.getCourseInformationSharingConfiguration() != CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }
    }

    /**
     * Checks if messaging or communication is enabled for the given course, otherwise throws a ResponseStatusException with status 403 (Forbidden)
     *
     * @param course the course to check
     */
    void checkCommunicationEnabledElseThrow(Course course) {
        if (course.getCourseInformationSharingConfiguration() == CourseInformationSharingConfiguration.DISABLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }
    }

    /**
     * Checks if messaging or communication is enabled for the course with the given id, otherwise throws a ResponseStatusException with status 403 (Forbidden)
     *
     * @param courseId the course to check
     */
    void checkCommunicationEnabledElseThrow(Long courseId) {
        if (!courseRepository.isCommunicationEnabled(courseId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is not enabled for this course");
        }
    }
}
