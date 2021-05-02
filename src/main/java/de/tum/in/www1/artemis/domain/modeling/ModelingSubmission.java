package de.tum.in.www1.artemis.domain.modeling;

import javax.persistence.*;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * A ModelingSubmission.
 */
@Entity
@DiscriminatorValue(value = "M")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelingSubmission extends Submission {

    @Column(name = "model")
    @Lob
    private String model;

    @Column(name = "explanation_text")
    @Lob
    private String explanationText;

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
}
