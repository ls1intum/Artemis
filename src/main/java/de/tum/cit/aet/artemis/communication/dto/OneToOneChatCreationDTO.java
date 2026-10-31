package de.tum.cit.aet.artemis.communication.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictLongDeserializer;

/**
 * Request body for creating a one-to-one chat. A one-to-one chat always has exactly one other participant, identified by exactly one of {@code userId} or {@code login}.
 *
 * @param userId the id of the other participant, a whole number (mutually exclusive with {@code login})
 * @param login  the login of the other participant (mutually exclusive with {@code userId})
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OneToOneChatCreationDTO(@Nullable @JsonDeserialize(using = StrictLongDeserializer.class) Long userId, @Nullable String login) {
}
