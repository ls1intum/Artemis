package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.ZonedDateTime;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.dto.payload.DuplicateTestCasePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user that a duplicate test case was found.
 */
@CourseNotificationType(12)
public class DuplicateTestCaseNotification extends CourseNotification {

    private final DuplicateTestCasePayloadDTO payload;

    /**
     * Default constructor used when creating a new duplicate test case notification.
     */
    public DuplicateTestCaseNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, String releaseDate, String dueDate,
            Long examId, Long exerciseGroupId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new DuplicateTestCasePayloadDTO(exerciseId, exerciseTitle, releaseDate, dueDate, examId, exerciseGroupId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public DuplicateTestCaseNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, DuplicateTestCasePayloadDTO.class);
    }

    @Override
    protected CourseNotificationDeliveryPolicy getDeliveryPolicy() {
        return CourseNotificationDeliveryPolicy.EXERCISE;
    }

    @Override
    public String getRelativeWebAppUrl() {
        if (payload.examId() != null && payload.exerciseGroupId() != null) {
            return "/course-management/" + courseId + "/exams/" + payload.examId() + "/exercise-groups/" + payload.exerciseGroupId() + "/programming-exercises/"
                    + payload.exerciseId();
        }
        return "/courses/" + courseId + "/exercises/" + payload.exerciseId();
    }

    @Override
    public DuplicateTestCasePayloadDTO payload() {
        return payload;
    }
}
