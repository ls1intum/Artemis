package de.tum.in.www1.artemis.domain.iris;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * An IrisSession represents a conversation between a user and an Artemis bot.
 * Currently, IrisSessions are only used to help students with programming exercises.
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

    public List<IrisMessage> getMessages() {
        return messages;
    }
}
