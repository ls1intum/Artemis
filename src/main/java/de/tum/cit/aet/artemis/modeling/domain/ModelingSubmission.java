package de.tum.cit.aet.artemis.modeling.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_SUBMISSION_MODEL_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_SUBMISSION_TEXT_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A ModelingSubmission.
 */
@Entity
@DiscriminatorValue(value = "M")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelingSubmission extends Submission {

    private static final Logger log = LoggerFactory.getLogger(ModelingSubmission.class);

    /** Property of an Apollon v3 model that holds its elements. */
    private static final String ELEMENTS_PROPERTY = "elements";

    /** Property of an Apollon v4 model that holds its nodes. */
    private static final String NODES_PROPERTY = "nodes";

    @Override
    public String getSubmissionExerciseType() {
        return "modeling";
    }

    @Column(name = "model")
    @Size(max = MAX_SUBMISSION_MODEL_LENGTH, message = "The modeling submission is too large.")
    private String model;

    @Column(name = "explanation_text")
    @Size(max = MAX_SUBMISSION_TEXT_LENGTH, message = "The explanation of the modeling submission is too large.")
    private String explanationText;

    public String getModel() {
        return model;
    }

    public ModelingSubmission model(String model) {
        this.model = model;
        return this;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public void setExplanationText(String explanationText) {
        this.explanationText = explanationText;
    }

    @Override
    public String toString() {
        return "ModelingSubmission{" + "id=" + getId() + "}";
    }

    /**
     * checks if the modeling submission is empty by using a new object mapper.
     * A modeling submission is empty if the model is null, blank (no actual characters) or if the elements/nodes in the json description are empty.
     * Supports Apollon v3 (with 'elements') and v4 (with 'nodes').
     *
     * @return true if the submission is empty, false otherwise
     */
    @Override
    public boolean isEmpty() {
        return isEmpty(JsonObjectMapper.get());
    }

    /**
     * checks if the modeling submission is empty by using a predefined object mapper (in case this is invoked multiple times).
     * A modeling submission is empty if the model is null, blank (no actual characters) or if the elements/nodes in the json description are empty.
     * Supports Apollon v3 (with 'elements') and v4 (with 'nodes').
     *
     * @param jacksonObjectMapper a predefined jackson object mapper
     * @return true if the submission is empty, false otherwise
     */
    public boolean isEmpty(JsonMapper jacksonObjectMapper) {
        try {
            // in case there is an explanation, we should
            if (StringUtils.hasText(explanationText)) {
                return false;
            }
            if (model == null || model.isBlank()) {
                return true;
            }
            try (JsonParser parser = jacksonObjectMapper.createParser(getModel())) {
                // The model is read as a token stream: its size is bounded only by MAX_SUBMISSION_MODEL_LENGTH, and only the emptiness of two properties matters.
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    // A model that is not a JSON object has neither elements nor nodes
                    parser.skipChildren();
                    return true;
                }
                Boolean elementsEmpty = null;
                boolean nodesEmpty = true;
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    String property = parser.currentName();
                    JsonToken valueToken = parser.nextToken();
                    // As with a parsed tree, a property that occurs twice counts with its last value
                    if (ELEMENTS_PROPERTY.equals(property)) {
                        elementsEmpty = consumeValueAndCheckEmpty(parser, valueToken);
                    }
                    else if (NODES_PROPERTY.equals(property)) {
                        nodesEmpty = consumeValueAndCheckEmpty(parser, valueToken);
                    }
                    else {
                        parser.skipChildren();
                    }
                }

                // Check for v3 (elements), then for v4 (nodes)
                return elementsEmpty != null ? elementsEmpty : nodesEmpty;
            }
        }
        catch (JacksonException ex) {
            log.warn("Failed to parse model JSON", ex);
            return false;
        }
    }

    /**
     * Reads the value the parser currently points at to its end and tells whether it is empty. An object or an array is empty if it has no members; a string, a number,
     * a boolean and null have no members and are therefore empty as well.
     *
     * @param parser     the parser, positioned at the first token of the value
     * @param valueToken the first token of the value
     * @return true if the value has no members, false otherwise
     */
    private static boolean consumeValueAndCheckEmpty(JsonParser parser, JsonToken valueToken) {
        if (!valueToken.isStructStart()) {
            return true;
        }
        JsonToken token = parser.nextToken();
        boolean empty = token.isStructEnd();
        // Read on to the closing token of the value: a nested value is skipped as a whole, so the first closing token that is seen at this level is the one of the value.
        while (!token.isStructEnd()) {
            if (token.isStructStart()) {
                parser.skipChildren();
            }
            token = parser.nextToken();
        }
        return empty;
    }
}
