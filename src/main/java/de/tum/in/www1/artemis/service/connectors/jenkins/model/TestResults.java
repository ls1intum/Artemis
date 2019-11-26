package de.tum.in.www1.artemis.service.connectors.jenkins.model;

import java.util.List;

public class TestResults {

    private String fullName;

    private List<String> commitHashes;

    private List<Testsuite> results;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public List<String> getCommitHashes() {
        return commitHashes;
    }

    public void setCommitHashes(List<String> commitHashes) {
        this.commitHashes = commitHashes;
    }

    public List<Testsuite> getResults() {
        return results;
    }

    public void setResults(List<Testsuite> results) {
        this.results = results;
    }
}
