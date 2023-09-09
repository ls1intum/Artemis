package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;

/**
 * A record which represents the conversation settings stored in a conversation participant for a conversation.
 *
 * @param conversationParticipantId the id of the according conversation participant
 * @param isModerator               true if the user is a moderator of a channel
 * @param isFavorite                true if the conversation is marked as favorite
 * @param isHidden                  true if the conversation is marked as hidden
 * @param lastRead                  the last date the participant read the messages in the conversation
 */
public record ConversationParticipantSettingsView(Long conversationParticipantId, Boolean isModerator, Boolean isFavorite, Boolean isHidden, ZonedDateTime lastRead) {
}
