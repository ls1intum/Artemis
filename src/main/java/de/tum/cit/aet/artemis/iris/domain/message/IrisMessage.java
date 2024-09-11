package de.tum.cit.aet.artemis.iris.domain.message;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

/**
 * An IrisMessage represents a single message in an IrisSession.
 * The message may contain multiple pieces of content with different types.
 */
@Entity
@Table(name = "iris_message")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisMessage extends DomainObject {

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    @JsonIgnore
    private IrisSession session;

    @Nullable
    @Column(name = "sent_at")
    private ZonedDateTime sentAt = ZonedDateTime.now();

    @Nullable
    @Column(name = "helpful")
    private Boolean helpful;

    @Column(name = "sender")
    @Enumerated(EnumType.STRING)
    private IrisMessageSender sender;

    @OrderColumn(name = "iris_message_content_order")
    @OneToMany(mappedBy = "message", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IrisMessageContent> content = new ArrayList<>();

    @Transient
    private Integer messageDifferentiator; // is supposed to be only a part of the dto and helps the client application to differentiate messages it should add to the message store

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

    public void addContent(IrisMessageContent... content) {
        for (IrisMessageContent c : content) {
            c.setMessage(this);
            this.content.add(c);
        }
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
        return "IrisMessage{" + "id=" + getId() + ", session=" + session + ", sentAt=" + sentAt + ", helpful=" + helpful + ", sender=" + sender + ", content=" + content
                + ", messageDifferentiator=" + messageDifferentiator + '}';
    }
}
