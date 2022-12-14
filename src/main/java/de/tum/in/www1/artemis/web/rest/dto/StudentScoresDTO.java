package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentScoresDTO {
    protected double absoluteScore;

    private double relativeScore;

    private double currentRelativeScore;

    private int presentationScore;

    public StudentScoresDTO(double absoluteScore, double relativeScore, double currentRelativeScore, int presentationScore) {
        this.absoluteScore = absoluteScore;
        this.relativeScore = relativeScore;
        this.currentRelativeScore = currentRelativeScore;
        this.presentationScore = presentationScore;
    }

    public StudentScoresDTO() {
        this.absoluteScore = 0.0;
        this.relativeScore = 0.0;
        this.currentRelativeScore = 0.0;
        this.presentationScore = 0;
    }

    public double getAbsoluteScore() {
        return absoluteScore;
    }

    public void setAbsoluteScore(double absoluteScore) {
        this.absoluteScore = absoluteScore;
    }

    public double getRelativeScore() {
        return relativeScore;
    }

    public void setRelativeScore(double relativeScore) {
        this.relativeScore = relativeScore;
    }

    public double getCurrentRelativeScore() {
        return currentRelativeScore;
    }

    public void setCurrentRelativeScore(double currentRelativeScore) {
        this.currentRelativeScore = currentRelativeScore;
    }

    public int getPresentationScore() {
        return presentationScore;
    }

    public void setPresentationScore(int presentationScore) {
        this.presentationScore = presentationScore;
    }
}
