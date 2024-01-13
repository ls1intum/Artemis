package de.tum.in.www1.artemis.domain.iris.message;

import javax.annotation.Nonnull;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.*;
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

    @Nonnull
    @Column(name = "json_content")
    @JsonRawValue
    @JsonProperty(value = "attributes", required = true)
    private String jsonContent;

    @Nonnull
    @Transient
    @JsonIgnore
    private JsonNode jsonNode;

    // Required by JPA
    public IrisJsonMessageContent() {
    }

    public IrisJsonMessageContent(@Nonnull JsonNode jsonNode) {
        this.jsonNode = jsonNode;
        this.jsonContent = jsonNode.toPrettyString();
    }

    @Override
    @JsonIgnore
    public String getContentAsString() {
        return jsonContent;
    }

    public void setJsonContent(@Nonnull String jsonContent) {
        try {
            this.jsonNode = new ObjectMapper().readTree(jsonContent);
            this.jsonContent = jsonContent;
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Error while loading Json content", e);
        }
    }

    @Nonnull
    public JsonNode getJsonNode() {
        return jsonNode;
    }

    public void setJsonNode(@Nonnull JsonNode jsonNode) {
        this.jsonNode = jsonNode;
        this.jsonContent = jsonNode.toPrettyString();
    }

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
