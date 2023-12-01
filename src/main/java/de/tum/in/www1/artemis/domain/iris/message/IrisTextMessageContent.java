package de.tum.in.www1.artemis.domain.iris.message;

import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    public IrisTextMessageContent() {
    }

    public IrisTextMessageContent(@Nullable String textContent) {
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
        return "IrisMessageContent{" + "message=" + (message == null ? "null" : message.getId()) + ", textContent='" + textContent + '\'' + '}';
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && Objects.equals(this.textContent, ((IrisTextMessageContent) obj).textContent);
    }
}
