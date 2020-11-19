package de.tum.in.www1.artemis.domain.plagiarism.modeling;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;

public class ModelingSubmissionElement extends PlagiarismSubmissionElement {

    private String modelElementId;

    public String getModelElementId() {
        return modelElementId;
    }

    public void setModelElementId(String modelElementId) {
        this.modelElementId = modelElementId;
    }
}
