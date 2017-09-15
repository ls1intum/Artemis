package de.tum.in.www1.exerciseapp.web.rest.util;

import de.tum.in.www1.exerciseapp.domain.Result;

/**
 * Class used to return a collection of exercise results together with the
 * number of submissions (results) for the participation the result belongs to
 */
public class ResultWithSubmissionCount {
    private Result actualResult;
    private Long submissionCount;

    public ResultWithSubmissionCount(Result result, Long submissionCount) {
        this.actualResult = result;
        this.submissionCount = submissionCount;
    }

    public Result getActualResult() {
        return actualResult;
    }

    public void setActualResult(Result actualResult) {
        this.actualResult = actualResult;
    }


    public Long getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(Long submissionCount) {
        this.submissionCount = submissionCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResultWithSubmissionCount that = (ResultWithSubmissionCount) o;

        if (actualResult != null ? !actualResult.equals(that.actualResult) : that.actualResult != null) return false;
        return submissionCount != null ? submissionCount.equals(that.submissionCount) : that.submissionCount == null;
    }

    @Override
    public int hashCode() {
        int result = actualResult != null ? actualResult.hashCode() : 0;
        result = 31 * result + (submissionCount != null ? submissionCount.hashCode() : 0);
        return result;
    }
}
