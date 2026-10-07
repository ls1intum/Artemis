package de.tum.cit.aet.artemis.notification.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Record to represent course notifications.
 * <p>
 * The type specific values live in {@link CourseNotificationPayloadDTO}, one record per notification type, so that a
 * reader knows what a notification of a given {@code notificationType} carries. A client narrows the payload on that
 * field rather than reaching into a map of objects.
 * <p>
 * The flat {@code parameters} map this record used to carry is still written alongside the typed fields, see
 * {@link #legacyParameters()}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationDTO(String notificationType, long notificationId, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category,
        String courseTitle, String courseIconUrl, CourseNotificationPayloadDTO payload, UserCourseNotificationStatusType status, String relativeWebAppUrl) implements Serializable {

    public CourseNotificationDTO(String notificationType, long notificationId, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category, String courseTitle,
            String courseIconUrl, CourseNotificationPayloadDTO payload, String relativeWebAppUrl) {
        this(notificationType, notificationId, courseId, creationDate, category, courseTitle, courseIconUrl, payload, UserCourseNotificationStatusType.SEEN, relativeWebAppUrl);
    }

    /**
     * The same values as {@code courseTitle}, {@code courseIconUrl} and {@code payload}, as the one flat map this
     * record used to carry, so that a client which has not migrated keeps reading notifications.
     * <p>
     * Derived rather than a component on purpose. A component would be written into the distributed store, where this
     * record is a cache value, and a {@code Map<String, Object>} there is exactly the untyped shape this record moved
     * away from. As a method it is invisible to Java serialization and to {@code DistributedDataSurfaceTest}, and only
     * Jackson sees it, which is the one place the compatibility is needed.
     * <p>
     * Read only, so that Jackson does not try to bind {@code parameters} back onto a record that has no such
     * component when a test or a client deserializes a response.
     * <p>
     * TODO: Remove once the clients that read {@code parameters} are out of circulation. The released iOS versions are
     * the ones that do, and ls1intum/artemis-ios#526 is what moves them off it; the web client reads {@code payload}
     * as of this release, and the Android app never read this shape over REST or websocket, only from the push body,
     * which is unaffected either way. Target sunset: 2027-03-31.
     *
     * @return the payload components and the values every notification carries, by name
     */
    @Deprecated(forRemoval = true, since = "10.0")
    @JsonProperty(value = "parameters", access = JsonProperty.Access.READ_ONLY)
    public Map<String, Object> legacyParameters() {
        return CourseNotificationPayloads.flatten(payload, courseTitle, courseIconUrl);
    }
}
