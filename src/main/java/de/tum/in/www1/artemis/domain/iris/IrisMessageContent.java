package de.tum.in.www1.artemis.domain.iris;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * An IrisMessageContent represents a part of the content of an IrisMessage.
 * For now, IrisMessageContent only supports text content.
 * In the future, we might want to support images and other content types.
 */
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

    @Override
    public String toString() {
        return "IrisMessageContent{" + "message=" + (message == null ? "null" : message.getId()) + ", textContent='" + textContent + '\'' + '}';
    }
}
