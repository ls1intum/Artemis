package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BambooBuildResultDTO {

    private BuildState buildState;

    private String buildTestSummary;

    private String buildReason;

    private ZonedDateTime buildCompletedDate;

    private BambooTestResultsDTO testResults;

    private String vcsRevisionKey;

    // TODO add proper DTOs for the following two attributes
    private Map<String, Object> changes;

    private Map<String, Object> artifacts;

    public String getVcsRevisionKey() {
        return vcsRevisionKey;
    }

    public void setVcsRevisionKey(String vcsRevisionKey) {
        this.vcsRevisionKey = vcsRevisionKey;
    }

    public Map<String, Object> getChanges() {
        return changes;
    }

    public void setChanges(Map<String, Object> changes) {
        this.changes = changes;
    }

    public Map<String, Object> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(Map<String, Object> artifacts) {
        this.artifacts = artifacts;
    }

    public BambooTestResultsDTO getTestResults() {
        return testResults;
    }

    public void setTestResults(BambooTestResultsDTO testResults) {
        this.testResults = testResults;
    }

    public BuildState getBuildState() {
        return buildState;
    }

    public void setBuildState(BuildState buildState) {
        this.buildState = buildState;
    }

    public String getBuildTestSummary() {
        return buildTestSummary;
    }

    public void setBuildTestSummary(String buildTestSummary) {
        this.buildTestSummary = buildTestSummary;
    }

    public String getBuildReason() {
        return buildReason;
    }

    public void setBuildReason(String buildReason) {
        this.buildReason = buildReason;
    }

    public ZonedDateTime getBuildCompletedDate() {
        return buildCompletedDate;
    }

    public void setBuildCompletedDate(ZonedDateTime buildCompletedDate) {
        this.buildCompletedDate = buildCompletedDate;
    }

    public static final class BambooTestResultsDTO {

        private int all;

        private int successful;

        private int failed;

        private int newFailed;

        private int existingFailed;

        private int fixed;

        private int quarantined;

        private int skipped;

        private BambooFailedTestsDTO failedTests;

        public int getAll() {
            return all;
        }

        public void setAll(int all) {
            this.all = all;
        }

        public int getSuccessful() {
            return successful;
        }

        public void setSuccessful(int successful) {
            this.successful = successful;
        }

        public int getFailed() {
            return failed;
        }

        public void setFailed(int failed) {
            this.failed = failed;
        }

        public int getNewFailed() {
            return newFailed;
        }

        public void setNewFailed(int newFailed) {
            this.newFailed = newFailed;
        }

        public int getExistingFailed() {
            return existingFailed;
        }

        public void setExistingFailed(int existingFailed) {
            this.existingFailed = existingFailed;
        }

        public int getFixed() {
            return fixed;
        }

        public void setFixed(int fixed) {
            this.fixed = fixed;
        }

        public int getQuarantined() {
            return quarantined;
        }

        public void setQuarantined(int quarantined) {
            this.quarantined = quarantined;
        }

        public int getSkipped() {
            return skipped;
        }

        public void setSkipped(int skipped) {
            this.skipped = skipped;
        }

        public BambooFailedTestsDTO getFailedTests() {
            return failedTests;
        }

        public void setFailedTests(BambooFailedTestsDTO failedTests) {
            this.failedTests = failedTests;
        }
    }

    public static final class BambooFailedTestsDTO {

        private int size;

        private String expand;

        @JsonProperty("testResult")
        private List<BambooTestResultDTO> testResults;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public String getExpand() {
            return expand;
        }

        public void setExpand(String expand) {
            this.expand = expand;
        }

        public List<BambooTestResultDTO> getTestResults() {
            return testResults;
        }

        public void setTestResults(List<BambooTestResultDTO> testResults) {
            this.testResults = testResults;
        }
    }

    public static final class BambooTestResultDTO {

        private String className;

        private String methodName;

        private String status;

        private int duration;

        private int durationInSeconds;

        private BambooTestResultErrorsDTO errors;

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getDuration() {
            return duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }

        public int getDurationInSeconds() {
            return durationInSeconds;
        }

        public void setDurationInSeconds(int durationInSeconds) {
            this.durationInSeconds = durationInSeconds;
        }

        public BambooTestResultErrorsDTO getErrors() {
            return errors;
        }

        public void setErrors(BambooTestResultErrorsDTO errors) {
            this.errors = errors;
        }
    }

    public static final class BambooTestResultErrorsDTO {

        private int size;

        @JsonProperty("max-result")
        private int maxResult;

        @JsonProperty("error")
        private List<BambooTestErrorDTO> errorMessages;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getMaxResult() {
            return maxResult;
        }

        public void setMaxResult(int maxResult) {
            this.maxResult = maxResult;
        }

        public List<BambooTestErrorDTO> getErrorMessages() {
            return errorMessages;
        }

        public void setErrorMessages(List<BambooTestErrorDTO> errorMessages) {
            this.errorMessages = errorMessages;
        }
    }

    public static final class BambooTestErrorDTO {

        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public enum BuildState {

        SUCCESS("Successful"), FAILED("Failed");

        private String state;

        BuildState(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }
}
