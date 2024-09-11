package de.tum.cit.aet.artemis.domain.iris.message;

import jakarta.annotation.Nullable;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.domain.DomainObject;

@Entity
@Table(name = "iris_message_content")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
        @JsonSubTypes.Type(value = IrisTextMessageContent.class, name = "text"),
        @JsonSubTypes.Type(value = IrisJsonMessageContent.class, name = "json"),
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisMessageContent extends DomainObject {

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "message_id")
    IrisMessage message;

    public IrisMessage getMessage() {
        return message;
    }

    public void setMessage(IrisMessage message) {
        this.message = message;
    }

    @Nullable
    public abstract String getContentAsString();

}
