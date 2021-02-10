package de.tum.in.www1.artemis.web.rest.dto;

/**
 * Contains various course scores values
 */
public class CourseScoresDTO {

    public double absoluteScore;

    public double relativeScore;

    public double maxPoints;

    public double presentationScore;

    public double reachableScore;

    public double currentRelativeScore;

    public CourseScoresDTO(double absoluteScore, double relativeScore, double maxPoints, double presentationScore, double reachableScore, double currentRelativeScore) {
        this.absoluteScore = absoluteScore;
        this.relativeScore = relativeScore;
        this.maxPoints = maxPoints;
        this.presentationScore = presentationScore;
        this.reachableScore = reachableScore;
        this.currentRelativeScore = currentRelativeScore;
    }

}
