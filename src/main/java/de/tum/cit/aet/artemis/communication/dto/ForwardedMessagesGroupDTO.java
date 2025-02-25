package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for grouping Forwarded Messages by ID.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ForwardedMessagesGroupDTO(Long id, Set<ForwardedMessageDTO> messages) {

}
