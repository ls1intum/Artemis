package de.tum.cit.aet.artemis.communication.domain;

import de.tum.cit.aet.artemis.account.domain.User;

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
 * @param isTestUser             whether the user is a test user, which gets no e-mail it did not ask for and must therefore stay recognizable where
 *                                   the user is rebuilt from this summary
 * @param isConversationMuted    whether the user muted the conversation
 * @param isConversationHidden   whether the user hid the conversation
 * @param isAtLeastTutorInCourse true if the user is at least a tutor in the course
 */
public record ConversationNotificationRecipientSummary(long userId, String userLogin, String firstName, String lastName, String userLangKey, String userEmail, boolean isTestUser,
        boolean isConversationMuted, boolean isConversationHidden, boolean isAtLeastTutorInCourse) {

    public ConversationNotificationRecipientSummary(User user, boolean isConversationMuted, boolean isConversationHidden, boolean isAtLeastTutorInCourse) {
        this(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getLangKey(), user.getEmail(), user.isTestUser(), isConversationMuted,
                isConversationHidden, isAtLeastTutorInCourse);
    }

    public boolean shouldNotifyRecipient() {
        return !isConversationMuted && !isConversationHidden;
    }

    /**
     * Rebuilds a light {@link User} from this summary, for the code that only has the summary and has to hand a user to the notifications. It carries
     * everything the notifications read, including the test user flag that decides whether an e-mail is sent.
     *
     * @return a user with the id, the login, the name, the language, the e-mail address and the test user flag of the recipient
     */
    public User toUser() {
        var user = new User(userId, userLogin, firstName, lastName, userLangKey, userEmail);
        user.setTestUser(isTestUser);
        return user;
    }
}
