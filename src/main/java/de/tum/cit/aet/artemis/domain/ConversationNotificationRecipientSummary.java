package de.tum.cit.aet.artemis.domain;

/**
 * Stores the user of a conversation participant, who is supposed to receive a websocket message and stores whether
 * the corresponding conversation is hidden by the user.
 *
 * @param userId                 the id of the user who is a member of the conversation
 * @param userLogin              the login of the user who is a member of the conversation
 * @param firstName              the first name of the user
 * @param lastName               the last name of the user
 * @param userLangKey            the language key of the user
 * @param userEmail              the email address of the user
 * @param isConversationMuted    whether the user muted the conversation
 * @param isConversationHidden   whether the user hid the conversation
 * @param isAtLeastTutorInCourse true if the user is at least a tutor in the course
 */
public record ConversationNotificationRecipientSummary(long userId, String userLogin, String firstName, String lastName, String userLangKey, String userEmail,
        boolean isConversationMuted, boolean isConversationHidden, boolean isAtLeastTutorInCourse) {

    public ConversationNotificationRecipientSummary(User user, boolean isConversationMuted, boolean isConversationHidden, boolean isAtLeastTutorInCourse) {
        this(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getLangKey(), user.getEmail(), isConversationMuted, isConversationHidden,
                isAtLeastTutorInCourse);
    }

    public boolean shouldNotifyRecipient() {
        return !isConversationMuted && !isConversationHidden;
    }
}
