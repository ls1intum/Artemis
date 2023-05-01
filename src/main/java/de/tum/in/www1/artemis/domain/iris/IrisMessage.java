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

import de.tum.in.www1.artemis.domain.DomainObject;

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
}
