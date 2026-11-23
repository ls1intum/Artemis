package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Only the public identity fields needed to format a mention suggestion. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConversationMentionCandidateDTO(String firstName, String lastName, String login) {
}
