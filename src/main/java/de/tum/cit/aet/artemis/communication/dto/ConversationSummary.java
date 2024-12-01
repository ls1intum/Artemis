package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;

/**
 * Summaries user related and general information about a conversation.
 *
 * @param conversation            the conversation the information belongs to
 * @param userConversationInfo    user related information, like the number of unread messages
 * @param generalConversationInfo general information about the conversation, like number of participants
 */
public record ConversationSummary(Conversation conversation, UserConversationInfo userConversationInfo, GeneralConversationInfo generalConversationInfo) {

}
