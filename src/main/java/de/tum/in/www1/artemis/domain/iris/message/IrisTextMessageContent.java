package de.tum.in.www1.artemis.domain.iris.message;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * An IrisMessageTextContent represents the text content of a message in an IrisSession.
 */
@Entity
@Table(name = "iris_text_message_content")
@DiscriminatorValue(value = "TEXT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTextMessageContent extends IrisMessageContent {
    
    @Nullable
    @Column(name = "text_content")
    private String textContent;
    
    // Required by JPA
    public IrisTextMessageContent() {}
    
    public IrisTextMessageContent(IrisMessage irisMessage, @Nullable String textContent) {
        super(irisMessage);
        this.textContent = textContent;
    }
    
    @Override
    public String getContentAsString() {
        return textContent;
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
        return "IrisMessageContent{"
                + "message=" + (message == null ? "null" : message.getId())
                + ", textContent='" + textContent + '\''
                + '}';
    }
}
