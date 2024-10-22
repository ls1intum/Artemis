package de.tum.cit.aet.artemis.communication.domain;

import java.util.Set;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Encapsulates data needed after a new message has been created in a conversation. This data is used to send notifications to involved users about the new message asynchronously
 *
 * @param messageWithHiddenDetails the new message with hidden details, i.e. conversation details
 * @param completeConversation     the conversation without hidden details
 * @param mentionedUsers           users mentioned in the message
 */
public record CreatedConversationMessage(Post messageWithHiddenDetails, Conversation completeConversation, Set<User> mentionedUsers) {

}
