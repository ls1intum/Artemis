package de.tum.in.www1.artemis.service.connectors.jenkins.model;

import java.time.ZonedDateTime;
import java.util.List;

public class TestResults {

    private int successful;

    private int skipped;

    private int errors;

    private int failures;

    private String fullName;

    private List<Commit> commits;

    private List<Testsuite> results;

    private ZonedDateTime runDate;

    public int getSuccessful() {
        return successful;
    }

    public void setSuccessful(int successful) {
        this.successful = successful;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }

    public int getFailures() {
        return failures;
    }

    public void setFailures(int failures) {
        this.failures = failures;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public ZonedDateTime getRunDate() {
        return runDate;
    }

    public void setRunDate(ZonedDateTime runDate) {
        this.runDate = runDate;
    }

    public List<Commit> getCommits() {
        return commits;
    }

    public void setCommits(List<Commit> commits) {
        this.commits = commits;
    }

    public List<Testsuite> getResults() {
        return results;
    }

    public void setResults(List<Testsuite> results) {
        this.results = results;
    }
}
