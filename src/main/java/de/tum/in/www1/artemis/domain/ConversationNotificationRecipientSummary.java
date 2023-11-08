package de.tum.in.www1.artemis.domain;

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
 * @param isConversationHidden   true if the user has hidden the conversation
 * @param isAtLeastTutorInCourse true if the user is at least a tutor in the course
 */
public record ConversationNotificationRecipientSummary(Long userId, String userLogin, String firstName, String lastName, String userLangKey, String userEmail,
        boolean isConversationHidden, boolean isAtLeastTutorInCourse) {

    public ConversationNotificationRecipientSummary(Long userId, String userLogin, String firstName, String lastName, String userLangKey, String userEmail,
            boolean isConversationHidden, boolean isAtLeastTutorInCourse) {
        this.userId = userId;
        this.userLogin = userLogin;
        this.firstName = firstName;
        this.lastName = lastName;
        this.userLangKey = userLangKey;
        this.userEmail = userEmail;
        this.isConversationHidden = isConversationHidden;
        this.isAtLeastTutorInCourse = isAtLeastTutorInCourse;
    }

    public ConversationNotificationRecipientSummary(User user, boolean isConversationHidden, boolean isAtLeastTutorInCourse) {
        this(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getLangKey(), user.getEmail(), isConversationHidden, isAtLeastTutorInCourse);
    }
}
