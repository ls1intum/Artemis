package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationSerializedDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.NewPostPayloadDTO;

/**
 * Guards the flat {@code parameters} map that {@link CourseNotificationDTO} keeps writing alongside its typed payload.
 * <p>
 * Released iOS versions decode {@code parameters} as a required key of every notification the REST list returns, so
 * dropping it fails the decode of the whole page rather than of the one value they cannot read. The map is derived
 * from the payload rather than stored, which is what keeps it out of the distributed store, and that is precisely the
 * kind of property no other test would notice losing: the shim has no caller inside the application.
 */
class CourseNotificationDTOTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final NewPostPayloadDTO PAYLOAD = new NewPostPayloadDTO(90037L, "content", 12L, "channel", "LECTURE", "Author", "image.url", 7L, false);

    private static CourseNotificationDTO notification(NewPostPayloadDTO payload) {
        return new CourseNotificationDTO("newPostNotification", 1L, 42L, ZonedDateTime.now(), CourseNotificationCategory.COMMUNICATION, "Course Title", "icon.url", payload,
                UserCourseNotificationStatusType.UNSEEN, "/courses/42/communication");
    }

    @Test
    void shouldWriteTheFlatParametersAlongsideTheTypedPayload() throws Exception {
        JsonNode json = MAPPER.valueToTree(notification(PAYLOAD));

        // The typed shape a migrated client narrows on its notificationType
        assertThat(json.path("payload").path("postId").asLong()).isEqualTo(90037L);
        assertThat(json.path("courseTitle").asString()).isEqualTo("Course Title");

        // The flat shape a client that has not migrated reads, which carries both at the same level
        JsonNode parameters = json.path("parameters");
        assertThat(parameters.path("postId").asLong()).isEqualTo(90037L);
        assertThat(parameters.path("channelName").asString()).isEqualTo("channel");
        assertThat(parameters.path("authorName").asString()).isEqualTo("Author");
        assertThat(parameters.path("courseTitle").asString()).isEqualTo("Course Title");
        assertThat(parameters.path("courseIconUrl").asString()).isEqualTo("icon.url");
    }

    @Test
    void shouldWriteTheSameParametersAsThePushBody() throws Exception {
        CourseNotificationDTO notification = notification(PAYLOAD);

        JsonNode fromRest = MAPPER.valueToTree(notification).path("parameters");
        JsonNode fromPush = MAPPER.valueToTree(new CourseNotificationSerializedDTO(notification)).path("parameters");

        // One implementation behind both, so a client reading either transport sees the same values under the same
        // names. A push notification and the list entry it links to disagreeing would be a bug nobody looks for.
        assertThat(fromRest).isEqualTo(fromPush);
    }

    @Test
    void shouldIgnoreParametersWhenReadingANotificationBack() {
        // No payload here: the sealed payload interface carries no type information, so a reader of this record only
        // ever gets the values every notification has. What matters is that the derived key does not fail the read,
        // which it would without an explicitly read only mapping, since the record has no such component.
        String json = """
                {"notificationType":"newPostNotification","notificationId":1,"courseId":42,"category":"COMMUNICATION",
                 "courseTitle":"Course Title","status":"UNSEEN","parameters":{"postId":90037,"courseTitle":"Course Title"}}
                """;

        assertThatCode(() -> {
            CourseNotificationDTO read = MAPPER.readValue(json, CourseNotificationDTO.class);
            assertThat(read.courseTitle()).isEqualTo("Course Title");
            assertThat(read.payload()).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldWriteThePayloadKeyEvenWhenEveryComponentIsNull() {
        // A client narrows on the presence of this key to tell a notification of this release from one of an earlier
        // one, so NON_EMPTY must not drop it for a notification whose payload happens to carry nothing.
        JsonNode json = MAPPER.valueToTree(notification(new NewPostPayloadDTO(null, null, null, null, null, null, null, null, false)));

        assertThat(json.has("payload")).isTrue();
    }

    @Test
    void shouldStillWriteTheSharedValuesWhenThePayloadIsAbsent() {
        JsonNode parameters = MAPPER.valueToTree(notification(null)).path("parameters");

        assertThat(parameters.path("courseTitle").asString()).isEqualTo("Course Title");
        assertThat(parameters.path("courseIconUrl").asString()).isEqualTo("icon.url");
    }
}
