package de.tum.in.www1.artemis.web.rest.dto;

import org.jetbrains.annotations.NotNull;

public class ModelingSubmissionComparisonDTO implements Comparable<ModelingSubmissionComparisonDTO> {

    private Long submissionId1;

    private Long submissionId2;

    private Long score1;

    private Long score2;

    private int size1;

    private int size2;

    private Double similarity;

    public Long getSubmissionId1() {
        return submissionId1;
    }

    public Long getSubmissionId2() {
        return submissionId2;
    }

    public Long getScore1() {
        return score1;
    }

    public Long getScore2() {
        return score2;
    }

    public int getSize1() {
        return size1;
    }

    public int getSize2() {
        return size2;
    }

    public Double getSimilarity() {
        return similarity;
    }

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

    public ModelingSubmissionComparisonDTO size1(int size1) {
        this.size1 = size1;
        return this;
    }

    public ModelingSubmissionComparisonDTO size2(int size2) {
        this.size2 = size2;
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
