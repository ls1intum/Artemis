package de.tum.cit.aet.artemis.notification.util;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloadDTO;

/**
 * Converts a notification payload to and from the key and value rows it is stored as.
 * <p>
 * Jackson rather than reflection over the payload's components: it already knows how to read {@code "90037"} back into
 * a {@code Long} and {@code "false"} into a {@code boolean}, and it fails loudly on a component it cannot map instead
 * of leaving it null. What it replaced walked the declared fields of the notification class and matched on
 * {@code getModifiers() == PROTECTED}, so adding {@code final} to a field silently dropped it from the payload.
 */
public final class CourseNotificationPayloads {

    /**
     * Deliberately private and unconfigured beyond these two settings, so that a payload conversion cannot be changed
     * by a global Jackson customization somewhere else in the application.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private CourseNotificationPayloads() {
    }

    /**
     * Reads a stored notification payload back.
     *
     * @param <T>        the payload type
     * @param parameters the stored rows, as key and value
     * @param type       the payload type of the notification being read
     * @return the payload, with the stored strings coerced into the component types
     */
    public static <T extends CourseNotificationPayloadDTO> T parse(Map<String, String> parameters, Class<T> type) {
        return MAPPER.convertValue(parameters, type);
    }

    /**
     * Flattens a payload into the values a client renders.
     * <p>
     * A mutable map, because the caller adds the values every notification carries regardless of its type.
     *
     * @param payload the payload to flatten
     * @return the components by name, with the types they are declared with
     */
    public static Map<String, Object> asMap(CourseNotificationPayloadDTO payload) {
        return new HashMap<>(MAPPER.convertValue(payload, new TypeReference<Map<String, Object>>() {
        }));
    }

    /**
     * The whole notification as one flat map: the payload's components plus the values every notification carries.
     * <p>
     * This is the shape a notification had on the wire before the payload was typed, and it is what the released
     * mobile clients read. It has two callers on purpose: the push notification body, whose shape is pinned by
     * {@code PushNotificationDataDTO}'s version, and the deprecated {@code parameters} property of
     * {@link de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO}, which keeps REST and websocket readable
     * by clients that have not migrated. Keeping one implementation is what makes those two shapes identical.
     *
     * @param payload       the type specific values, absent only for a notification built without one
     * @param courseTitle   the title of the course the notification belongs to
     * @param courseIconUrl the icon of that course, absent when it has none
     * @return the flattened values by name
     */
    public static Map<String, Object> flatten(CourseNotificationPayloadDTO payload, String courseTitle, String courseIconUrl) {
        // A notification always has a payload; tolerating its absence here keeps a half built one from failing
        // serialization of the whole page rather than of the one notification that is wrong.
        Map<String, Object> values = payload == null ? new HashMap<>() : asMap(payload);
        values.put("courseTitle", courseTitle);
        values.put("courseIconUrl", courseIconUrl);
        return values;
    }
}
