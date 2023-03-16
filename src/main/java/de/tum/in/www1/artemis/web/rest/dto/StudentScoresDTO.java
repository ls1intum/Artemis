package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the scores of a student in a course as returned from the server.
 * This is the parent class of the StudentScoresForExamBonusSourceDTO and thus cannot be a record.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentScoresDTO {

    protected double absoluteScore;

    private final double relativeScore;

    private final double currentRelativeScore;

    private final int presentationScore;

    /**
     * @param absoluteScore        the points achieved by the student in the course.
     * @param relativeScore        the points achieved by the student in the course divided by the max number of points achievable in the course (an exercise is added to these max
     *                                 points even if its assessment is not done yet).
     * @param currentRelativeScore the points achieved by the student in the course divided by the max number of points reachable in the course (an exercise is not added to these
     *                                 max points if its assessment is not done yet).
     * @param presentationScore    the presentation scores achieved by the student in the course.
     */
    public StudentScoresDTO(double absoluteScore, double relativeScore, double currentRelativeScore, int presentationScore) {
        this.absoluteScore = absoluteScore;
        this.relativeScore = relativeScore;
        this.currentRelativeScore = currentRelativeScore;
        this.presentationScore = presentationScore;
    }

    public double getAbsoluteScore() {
        return absoluteScore;
    }

    public double getRelativeScore() {
        return relativeScore;
    }

    public double getCurrentRelativeScore() {
        return currentRelativeScore;
    }

    public int getPresentationScore() {
        return presentationScore;
    }
}
