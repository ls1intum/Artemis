package de.tum.cit.aet.artemis.iris.domain.session;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;

/**
 * An IrisSession represents a list of messages of Artemis, a user, and an LLM.
 * See {@link IrisExerciseChatSession} and {@link IrisCourseChatSession} for concrete implementations.
 */
@Entity
@Table(name = "iris_session")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = IrisExerciseChatSession.class, name = "chat"), // TODO: Legacy. Should ideally be "exercise_chat"
    @JsonSubTypes.Type(value = IrisCourseChatSession.class, name = "course_chat"),
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSession extends DomainObject {

    @OrderColumn(name = "iris_message_order")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IrisMessage> messages = new ArrayList<>();

    @Column(name = "creation_date")
    private ZonedDateTime creationDate = ZonedDateTime.now();

    /**
     * The latest suggestions that were sent to the user.
     * This column holds the list of latest suggestions as a JSON array.
     */
    @Column(name = "latest_suggestions")
    private String latestSuggestions;

    // TODO: This is only used in the tests -> Remove
    public IrisMessage newMessage() {
        var message = new IrisMessage();
        message.setSession(this);
        return message;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public List<IrisMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<IrisMessage> messages) {
        this.messages = messages;
    }

    public String getLatestSuggestions() {
        return latestSuggestions;
    }

    public void setLatestSuggestions(String latestSuggestions) {
        this.latestSuggestions = latestSuggestions;
    }

}
