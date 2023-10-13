package de.tum.in.www1.artemis.domain.iris.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.DomainObject;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.annotation.Nullable;
import javax.persistence.*;

@Entity
@Table(name = "iris_message_content")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisMessageContent extends DomainObject {
    
    @ManyToOne
    @JsonIgnore
    IrisMessage message;
    
    // Required by JPA
    public IrisMessageContent() {}
    
    public IrisMessageContent(IrisMessage irisMessage) {
        this.message = irisMessage;
    }
    
    public IrisMessage getMessage() {
        return message;
    }
    
    public void setMessage(IrisMessage message) {
        this.message = message;
    }
    
    @Nullable
    public abstract String getContentAsString();

}
