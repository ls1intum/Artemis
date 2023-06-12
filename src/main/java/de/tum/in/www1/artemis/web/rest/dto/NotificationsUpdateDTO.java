package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Includes all ids that the client should subscribe to for notifications about updates
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NotificationsUpdateDTO(List<Long> tutorialGroupIds, List<Long> conversationIds) {
}
