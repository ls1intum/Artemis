package de.tum.in.www1.artemis.domain;

/**
 * A helper object that aggregates feedback list, complaint response, score and result string which are used to update an assessment after a complaint.
 */
public class ProgrammingAssessmentUpdate extends AssessmentUpdate {

    private String resultString;

    private long score;

    public String getResultString() {
        return resultString;
    }

    public void setResultString(String resultString) {
        this.resultString = resultString;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }
}
