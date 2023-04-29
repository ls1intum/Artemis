package de.tum.in.www1.artemis.domain.iris;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "iris_message_content")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisMessageContent extends DomainObject {

    @ManyToOne
    @JsonIgnore
    private IrisMessage message;

    @Nullable
    @Column(name = "text_content")
    private String textContent;

    public IrisMessage getMessage() {
        return message;
    }

    public void setMessage(IrisMessage message) {
        this.message = message;
    }

    @Nullable
    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(@Nullable String textContent) {
        this.textContent = textContent;
    }
}
