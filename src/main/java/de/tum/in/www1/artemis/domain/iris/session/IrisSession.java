package de.tum.in.www1.artemis.domain.iris.session;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;

/**
 * An IrisSession represents a list of messages of Artemis, a user, and an LLM.
 * See {@link IrisChatSession} and {@link IrisHestiaSession} for concrete implementations.
 */
@Entity
@Table(name = "iris_session")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = IrisChatSession.class, name = "chat"), @JsonSubTypes.Type(value = IrisHestiaSession.class, name = "hestia") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSession extends DomainObject {

    @OrderColumn(name = "iris_message_order")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IrisMessage> messages = new ArrayList<>();

    @Column(name = "creation_date")
    private ZonedDateTime creationDate;

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public List<IrisMessage> getMessages() {
        return messages;
    }
}
