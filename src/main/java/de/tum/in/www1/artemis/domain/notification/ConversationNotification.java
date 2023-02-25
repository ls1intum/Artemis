package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;

/**
 * A Notification concerning all new messages in a conversation.
 */
@Entity
@DiscriminatorValue("C")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ConversationNotification extends Notification {

    @JsonIgnore
    public static final Set<NotificationType> CONVERSATION_NOTIFICATION_TYPES = Set.of(CONVERSATION_NEW_MESSAGE, CONVERSATION_NEW_REPLY_MESSAGE);

    @ManyToOne
    @JoinColumn(name = "message_id")
    private Post message;

    @ManyToOne
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Transient
    @JsonIgnore
    public NotificationType notificationType;

    public ConversationNotification() {
        // Empty constructor needed for Jackson.
    }

    public ConversationNotification(Post message, Conversation conversation, String title, String text, NotificationType notificationType) {
        verifySupportedNotificationType(notificationType);
        this.notificationType = notificationType;
        this.setMessage(message);
        this.setConversation(conversation);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(text);
    }

    public Post getMessage() {
        return message;
    }

    public void setMessage(Post message) {
        this.message = message;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    /**
     * Websocket notification channel for conversation notifications of a specific conversation
     *
     * @return the channel
     */
    public String getTopic() {
        return "/topic/conversation/" + message.getConversation().getId() + "/notifications";
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    /**
     * Verifies that the given notification type is supported as a conversation notification type
     *
     * @param notificationType the notification type to verify
     */
    public static void verifySupportedNotificationType(NotificationType notificationType) {
        if (!CONVERSATION_NOTIFICATION_TYPES.contains(notificationType)) {
            throw new UnsupportedOperationException("Unsupported NotificationType for conversation: " + notificationType);
        }
    }
}
