package de.tum.cit.aet.artemis.notification.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One placeholder of a notification text.
 * <p>
 * This is the value of a distributed cache, which is why it is a record of two strings rather than the entity. The
 * entity references the {@code CourseNotification} it belongs to, which reaches the rest of the domain model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationParameterDTO(String key, String value) implements Serializable {
}
