package de.tum.cit.aet.artemis.modeling.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_SUBMISSION_MODEL_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_SUBMISSION_TEXT_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Size;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A ModelingSubmission.
 */
@Entity
@DiscriminatorValue(value = "M")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelingSubmission extends Submission {

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
     * Supports both Apollon v2 (with 'elements') and v3 (with 'nodes').
     *
     * @return true if the submission is empty, false otherwise
     */
    @Override
    public boolean isEmpty() {
        return isEmpty(new ObjectMapper());
    }

    /**
     * checks if the modeling submission is empty by using a predefined object mapper (in case this is invoked multiple times).
     * A modeling submission is empty if the model is null, blank (no actual characters) or if the elements/nodes in the json description are empty.
     * Supports both Apollon v2 (with 'elements') and v3 (with 'nodes').
     *
     * @param jacksonObjectMapper a predefined jackson object mapper
     * @return true if the submission is empty, false otherwise
     */
    public boolean isEmpty(ObjectMapper jacksonObjectMapper) {
        try {
            // in case there is an explanation, we should
            if (StringUtils.hasText(explanationText)) {
                return false;
            }
            // TODO: further improve this!!
            if (model == null || model.isBlank()) {
                return true;
            }
            var jsonNode = jacksonObjectMapper.readTree(getModel());

            // Check for v2 format (elements)
            var elements = jsonNode.get("elements");
            if (elements != null) {
                return elements.isEmpty();
            }

            // Check for v3 format (nodes)
            var nodes = jsonNode.get("nodes");
            return nodes == null || nodes.isEmpty();
        }
        catch (JsonProcessingException ex) {
            log.warn("Failed to parse model JSON", ex);
            return false;
        }
    }
}
