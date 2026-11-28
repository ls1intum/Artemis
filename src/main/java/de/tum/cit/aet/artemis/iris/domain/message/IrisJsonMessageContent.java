package de.tum.cit.aet.artemis.iris.domain.message;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.RawJsonStringDeserializer;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * An IrisJsonMessageContent represents the content of a message in an IrisSession as a JSON object, stored as its JSON text.
 * <p>
 * The shape depends on the kind of message: a multiple-choice question, a summary of the conversation, a context switch marker or a command marker. Code that knows the
 * shape reads and writes it as a record through {@link #readAs} and {@link #of}.
 */
@Entity
@Table(name = "iris_json_message_content")
@DiscriminatorValue(value = "JSON")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisJsonMessageContent extends IrisMessageContent {

    @NonNull
    @Column(name = "json_content")
    private String jsonContent = "{}";

    // Required by JPA
    public IrisJsonMessageContent() {
    }

    /**
     * Creates the content from a record, which is stored as its pretty-printed JSON.
     *
     * @param value the record holding the content
     * @return the new content
     */
    public static IrisJsonMessageContent of(@NonNull Record value) {
        var content = new IrisJsonMessageContent();
        content.replaceContent(value);
        return content;
    }

    /**
     * Creates the content from JSON text, which is stored exactly as given.
     *
     * @param jsonContent the JSON text
     * @return the new content
     * @throws RuntimeException if the text is not a single valid JSON value
     */
    public static IrisJsonMessageContent ofJson(@NonNull String jsonContent) {
        var content = new IrisJsonMessageContent();
        content.setJsonContent(jsonContent);
        return content;
    }

    @JsonProperty(value = "attributes", required = true)
    @JsonRawValue
    public String getAttributes() {
        return jsonContent;
    }

    /**
     * Sets the content from the {@code attributes} JSON value of a serialized content.
     *
     * @param attributes the JSON text of the value
     */
    @JsonProperty(value = "attributes", required = true)
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    public void setAttributes(String attributes) {
        setJsonContent(attributes);
    }

    @Override
    public String getContentAsString() {
        return jsonContent;
    }

    /**
     * Sets the content of this message as a JSON string.
     * The string is checked to be a single valid JSON value and then stored exactly as given.
     *
     * @param jsonContent The JSON string to set as content
     */
    public void setJsonContent(@NonNull String jsonContent) {
        try (JsonParser parser = JsonObjectMapper.get().createParser(jsonContent)) {
            if (parser.nextToken() == null) {
                throw new IllegalArgumentException("Json content must not be empty");
            }
            parser.skipChildren();
            JsonToken trailing = parser.nextToken();
            if (trailing != null) {
                throw new IllegalArgumentException("Json content has trailing content: " + trailing);
            }
            this.jsonContent = jsonContent;
        }
        catch (JacksonException | IllegalArgumentException e) {
            throw new RuntimeException("Error while loading Json content", e);
        }
    }

    /**
     * Reads the content as the given record type. Properties the record does not declare are ignored.
     *
     * @param type the record type to read the content as
     * @param <T>  the record type
     * @return the content as the record
     * @throws JacksonException if the content does not have the shape of the record
     */
    public <T extends Record> T readAs(Class<T> type) {
        return JsonObjectMapper.get().readValue(jsonContent, type);
    }

    /**
     * Replaces the content of this message with a record, which is stored as its pretty-printed JSON.
     *
     * @param value the record holding the new content
     */
    public void replaceContent(@NonNull Record value) {
        this.jsonContent = JsonObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    @Override
    public String toString() {
        return "IrisMessageContent{" + "message=" + (message == null ? "null" : message.getId()) + ", textContent='" + getContentAsString() + '\'' + '}';
    }
}
