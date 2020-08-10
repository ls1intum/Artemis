package de.tum.in.www1.artemis.web.rest.dto;

import org.jetbrains.annotations.NotNull;

public class ModelingSubmissionComparisonDTO implements Comparable<ModelingSubmissionComparisonDTO> {

    public Long submissionId1;

    public Long submissionId2;

    public Long score1;

    public Long score2;

    public Double similarity;

    public ModelingSubmissionComparisonDTO similarity(Double similarity) {
        this.similarity = similarity;
        return this;
    }

    public ModelingSubmissionComparisonDTO submissionId1(Long submissionId1) {
        this.submissionId1 = submissionId1;
        return this;
    }

    public ModelingSubmissionComparisonDTO submissionId2(Long submissionId2) {
        this.submissionId2 = submissionId2;
        return this;
    }

    public ModelingSubmissionComparisonDTO score1(Long score1) {
        this.score1 = score1;
        return this;
    }

    public ModelingSubmissionComparisonDTO score2(Long score2) {
        this.score2 = score2;
        return this;
    }

    @Override
    public int compareTo(@NotNull ModelingSubmissionComparisonDTO other) {
        return Double.compare(similarity, other.similarity);
    }

    @Override
    public String toString() {
        return "SubmissionComparisonDTO{submission1=" + submissionId1 + ", submission2=" + submissionId2 + ", similarity=" + similarity + "}";
    }
}
