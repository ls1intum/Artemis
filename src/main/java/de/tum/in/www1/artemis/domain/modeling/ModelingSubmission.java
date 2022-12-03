package de.tum.in.www1.artemis.domain.modeling;

import static de.tum.in.www1.artemis.config.Constants.MAX_SUBMISSION_MODEL_LENGTH;
import static de.tum.in.www1.artemis.config.Constants.MAX_SUBMISSION_TEXT_LENGTH;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * A ModelingSubmission.
 */
@Entity
@DiscriminatorValue(value = "M")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelingSubmission extends Submission {

    @Column(name = "model")
    @Size(max = MAX_SUBMISSION_MODEL_LENGTH, message = "The modeling submission is too large.")
    @Lob
    private String model;

    @Column(name = "explanation_text")
    @Size(max = MAX_SUBMISSION_TEXT_LENGTH, message = "The explanation of the modeling submission is too large.")
    @Lob
    private String explanationText;

    @Transient
    @JsonSerialize
    private Set<SimilarElementCount> similarElementCounts = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

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
     * A modeling submission is empty if the model is null, blank (no actual characters) or if the elements in the json description are empty.
     *
     * @return true if the submission is empty, false otherwise
     */
    public boolean isEmpty() {
        return isEmpty(new ObjectMapper());
    }

    /**
     * checks if the modeling submission is empty by using a predefined object mapper (in case this is invoked multiple times).
     * A modeling submission is empty if the model is null, blank (no actual characters) or if the elements in the json description are empty.
     *
     * @param jacksonObjectMapper a predefined jackson object mapper
     *
     * @return true if the submission is empty, false otherwise
     */
    public boolean isEmpty(ObjectMapper jacksonObjectMapper) {
        try {
            // in case there is an explanation, we should
            if (StringUtils.hasText(explanationText)) {
                return false;
            }
            // TODO: further improve this!!
            return model == null || model.isBlank() || jacksonObjectMapper.readTree(getModel()).get("elements").isEmpty();
        }
        catch (JsonProcessingException ex) {
            return false;
        }
    }

    public Set<SimilarElementCount> getSimilarElements() {
        return similarElementCounts;
    }

    public void addSimilarElement(SimilarElementCount element) {
        this.similarElementCounts.add(element);
    }

    public void setSimilarElements(Set<SimilarElementCount> elementCounts) {
        this.similarElementCounts = elementCounts;
    }
}
