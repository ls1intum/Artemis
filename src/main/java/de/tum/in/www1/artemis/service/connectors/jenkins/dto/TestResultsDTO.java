package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestResultsDTO extends AbstractBuildResultNotificationDTO {

    private int successful;

    private int skipped;

    private int errors;

    private int failures;

    private String fullName;

    private List<CommitDTO> commits;

    private List<TestsuiteDTO> results;

    private List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports;

    private ZonedDateTime runDate;

    public static TestResultsDTO convert(Object someResult) {
        return new ObjectMapper().registerModule(new JavaTimeModule()).convertValue(someResult, TestResultsDTO.class);
    }

    public Integer getSuccessful() {
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

    @Override
    public ZonedDateTime getBuildRunDate() {
        return getRunDate();
    }

    @Override
    public Optional<String> getCommitHashFromAssignmentRepo() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = getCommits().stream().filter(commit -> !commit.getRepositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::getHash);
    }

    @Override
    public Optional<String> getCommitHashFromTestsRepo() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = getCommits().stream().filter(commit -> commit.getRepositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::getHash);
    }

    private int getSum() {
        return getSkipped() + getFailures() + getErrors() + getSuccessful();
    }

    @Override
    public boolean isBuildSuccessful() {
        return getSuccessful() == getSum();
    }

    @Override
    public Double getBuildScore() {
        final var testSum = getSum();
        if (testSum == 0) {
            return 0D;
        }
        return (getSuccessful().doubleValue() / testSum) * 100D;
    }

    @Override
    public String getTestsPassedString() {
        return String.format("%d of %d passed", getSuccessful(), getSum());
    }

    public void setRunDate(ZonedDateTime runDate) {
        this.runDate = runDate;
    }

    public List<CommitDTO> getCommits() {
        return commits;
    }

    public void setCommits(List<CommitDTO> commits) {
        this.commits = commits;
    }

    public List<TestsuiteDTO> getResults() {
        return results;
    }

    public void setResults(List<TestsuiteDTO> results) {
        this.results = results;
    }

    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return staticCodeAnalysisReports;
    }

    public void setStaticCodeAnalysisReports(List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
        this.staticCodeAnalysisReports = staticCodeAnalysisReports;
    }
}
