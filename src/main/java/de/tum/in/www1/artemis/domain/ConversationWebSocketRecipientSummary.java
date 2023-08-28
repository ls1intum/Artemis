package de.tum.in.www1.artemis.domain;

/**
 * Stores the user of a conversation participant, who is supposed to receive a websocket message and stores whether
 * the according conversation is hidden by the user.
 *
 * @param user
 * @param isConversationHidden
 */
public record ConversationWebSocketRecipientSummary(User user, boolean isConversationHidden) {

}
