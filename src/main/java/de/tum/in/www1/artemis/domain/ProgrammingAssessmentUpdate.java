package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

/**
 * A helper object that aggregates a feedback list and a complaint response which is used to update an assessment after a complaint.
 */
public class ProgrammingAssessmentUpdate extends AssessmentUpdate {

    private String resultString;

    private ZonedDateTime completionDate;

    private long score;

    public String getResultString() {
        return resultString;
    }

    public void setResultString(String resultString) {
        this.resultString = resultString;
    }

    public ZonedDateTime getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(ZonedDateTime completionDate) {
        this.completionDate = completionDate;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }
}
