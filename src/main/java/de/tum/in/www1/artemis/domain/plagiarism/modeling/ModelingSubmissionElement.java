package de.tum.in.www1.artemis.domain.plagiarism.modeling;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class ModelingSubmissionElement extends PlagiarismSubmissionElement {

    private String modelElementId;

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
}
