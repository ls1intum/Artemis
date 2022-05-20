package de.tum.in.www1.artemis.domain.plagiarism.modeling;

import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

@Entity
// TODO: use @DiscriminatorValue(value = "M") or even better use integers (because they use less space in the database)
public class ModelingSubmissionElement extends PlagiarismSubmissionElement {

    private String modelElementId;

    /**
     * Create a new ModelingSubmissionElement instance from an existing UMLElement
     *
     * @param umlElement the UMLElement to create the ModelingSubmissionElement from
     * @return a new ModelingSubmissionElement instance
     */
    public static ModelingSubmissionElement fromUMLElement(UMLElement umlElement) {
        ModelingSubmissionElement element = new ModelingSubmissionElement();

        element.setModelElementId(umlElement.getJSONElementID());

        return element;
    }

    public String getModelElementId() {
        return modelElementId;
    }

    public void setModelElementId(String modelElementId) {
        this.modelElementId = modelElementId;
    }

    @Override
    public String toString() {
        return "ModelingSubmissionElement{" + "modelElementId='" + modelElementId + '\'' + '}';
    }
}
