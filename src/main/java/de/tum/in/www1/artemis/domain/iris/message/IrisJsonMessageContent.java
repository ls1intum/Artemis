package de.tum.in.www1.artemis.domain.iris.message;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An IrisJsonMessageContent represents the content of a message in an IrisSession as an arbitrary JSON object.
 */
@Entity
@Table(name = "iris_json_message_content")
@DiscriminatorValue(value = "JSON")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisJsonMessageContent extends IrisMessageContent {

    @NonNull
    @Column(name = "json_content")
    @JsonRawValue
    @JsonProperty(value = "attributes", required = true)
    private String jsonContent = "{}";

    @NonNull
    @Transient
    @JsonIgnore
    private JsonNode jsonNode = new ObjectMapper().createObjectNode();

    // Required by JPA
    public IrisJsonMessageContent() {
    }

    public IrisJsonMessageContent(@NonNull JsonNode jsonNode) {
        this.jsonNode = jsonNode;
        this.jsonContent = jsonNode.toPrettyString();
    }

    @Override
    public String getContentAsString() {
        return jsonContent;
    }

    /**
     * Sets the content of this message as a JSON string.
     * The string will be parsed into a JsonNode and stored in the jsonNode field.
     *
     * @param jsonContent The JSON string to set as content
     */
    public void setJsonContent(@NonNull String jsonContent) {
        try {
            this.jsonNode = new ObjectMapper().readTree(jsonContent);
            this.jsonContent = jsonContent;
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Error while loading Json content", e);
        }
    }

    @NonNull
    public JsonNode getJsonNode() {
        return jsonNode;
    }

    /**
     * Sets the content of this message as a JsonNode.
     * The JsonNode will be serialized into a JSON string and stored in the jsonContent field.
     *
     * @param jsonNode The JsonNode to set as content
     */
    public void setJsonNode(@NonNull JsonNode jsonNode) {
        this.jsonNode = jsonNode;
        this.jsonContent = jsonNode.toPrettyString();
    }

    /**
     * Loads the JsonNode from the jsonContent field after the entity has been loaded from the database.
     */
    @PostLoad
    private void postLoad() {
        try {
            this.jsonNode = new ObjectMapper().readTree(jsonContent);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Error while loading Json content", e);
        }
    }

    @Override
    public String toString() {
        return "IrisMessageContent{" + "message=" + (message == null ? "null" : message.getId()) + ", textContent='" + getContentAsString() + '\'' + '}';
    }
}
