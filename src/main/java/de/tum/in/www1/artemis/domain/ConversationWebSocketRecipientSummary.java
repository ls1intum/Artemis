package de.tum.in.www1.artemis.domain;

/**
 * Stores the user of a conversation participant, who is supposed to receive a websocket message and stores whether
 * the corresponding conversation is hidden by the user.
 *
 * @param userId                 the id of the user who is a member of the conversation
 * @param userLogin              the login of the user who is a member of the conversation
 * @param userLangKey            the language key of the user
 * @param userEmail              the email address of the user
 * @param isConversationHidden   true if the user has hidden the conversation
 * @param isAtLeastTutorInCourse true if the user is at least a tutor in the course
 */
public record ConversationWebSocketRecipientSummary(Long userId, String userLogin, String userLangKey, String userEmail, boolean isConversationHidden,
        boolean isAtLeastTutorInCourse) {
}
