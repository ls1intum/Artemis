package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;

/**
 * A Notification concerning all new messages in a conversation.
 */
@Entity
@DiscriminatorValue("C")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ConversationNotification extends Notification {

    @ManyToOne
    @JoinColumn(name = "message_id")
    private Post message;

    @ManyToOne
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    public ConversationNotification() {
        // Empty constructor needed for Jackson.
    }

    public ConversationNotification(User author, Post message, Conversation conversation, String title, String text, boolean textIsPlaceholder, String[] placeholderValues) {
        this.setMessage(message);
        this.setConversation(conversation);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(text);
        this.setAuthor(author);
        this.setPlaceholderValues(placeholderValues);
        this.setTextIsPlaceholder(textIsPlaceholder);
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
     * Websocket notification channel, for conversation notifications of a specific user
     *
     * @param userId id of user that should be notified
     * @return the channel
     */
    public String getTopic(long userId) {
        return "/topic/user/" + userId + "/notifications/conversations";
    }
}
