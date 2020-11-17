package de.tum.in.www1.artemis.web.rest.dto;

import javax.validation.constraints.NotNull;

public class ModelingSubmissionComparisonDTO implements Comparable<ModelingSubmissionComparisonDTO> {

    private ModelingSubmissionComparisonElement element1;

    private ModelingSubmissionComparisonElement element2;

    private Double similarity;

    public Double getSimilarity() {
        return similarity;
    }

    public ModelingSubmissionComparisonDTO similarity(Double similarity) {
        this.similarity = similarity;
        return this;
    }

    public ModelingSubmissionComparisonElement getElement1() {
        return element1;
    }

    public ModelingSubmissionComparisonElement getElement2() {
        return element2;
    }

    public void setElement1(ModelingSubmissionComparisonElement element1) {
        this.element1 = element1;
    }

    public void setElement2(ModelingSubmissionComparisonElement element2) {
        this.element2 = element2;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    @Override
    public int compareTo(@NotNull ModelingSubmissionComparisonDTO other) {
        return Double.compare(similarity, other.similarity);
    }

    @Override
    public String toString() {
        return "SubmissionComparisonDTO{submission1=" + element1.getSubmissionId() + ", submission2=" + element2.getSubmissionId() + ", similarity=" + similarity + "}";
    }
}
