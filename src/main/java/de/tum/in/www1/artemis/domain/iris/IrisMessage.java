package de.tum.in.www1.artemis.domain.iris;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;

/**
 * An IrisMessage represents a single message in an IrisSession.
 * A message can be created by either the user, an LLM, or Artemis.
 * Artemis messages are used to give commands to the bot and are not displayed to the user.
 */
@Entity
@Table(name = "iris_message")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisMessage extends DomainObject {

    @ManyToOne
    @JsonIgnore
    private IrisSession session;

    @Nullable
    @Column(name = "sent_at")
    private ZonedDateTime sentAt;

    @Nullable
    @Column(name = "helpful")
    private Boolean helpful;

    @Column(name = "sender")
    @Enumerated(EnumType.STRING)
    private IrisMessageSender sender;

    @Transient
    private Integer messageDifferentiator; // is supposed to be only a part of the dto and helps the client application to differentiate messages it should add to the message store

    @OrderColumn(name = "iris_message_content_order")
    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IrisMessageContent> content = new ArrayList<>();

    public IrisSession getSession() {
        return session;
    }

    public void setSession(IrisSession session) {
        this.session = session;
    }

    @Nullable
    public ZonedDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(@Nullable ZonedDateTime sentAt) {
        this.sentAt = sentAt;
    }

    @Nullable
    public Boolean getHelpful() {
        return helpful;
    }

    public void setHelpful(@Nullable Boolean helpful) {
        this.helpful = helpful;
    }

    public IrisMessageSender getSender() {
        return sender;
    }

    public void setSender(IrisMessageSender sender) {
        this.sender = sender;
    }

    public List<IrisMessageContent> getContent() {
        return content;
    }

    public void setContent(List<IrisMessageContent> content) {
        this.content = content;
    }

    @JsonProperty
    public Integer getMessageDifferentiator() {
        return messageDifferentiator;
    }

    @JsonProperty
    public void setMessageDifferentiator(Integer messageDifferentiator) {
        this.messageDifferentiator = messageDifferentiator;
    }

    @Override
    public String toString() {
        return "IrisMessage{" + "session=" + (session == null ? "null" : session.getId()) + ", sentAt=" + sentAt + ", helpful=" + helpful + ", sender=" + sender + '}';
    }
}
