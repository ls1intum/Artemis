package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.StringEscapeUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BambooBuildResultNotificationDTO extends AbstractBuildResultNotificationDTO {

    private String secret;

    private String notificationType;

    private BambooBuildPlanDTO plan;

    private BambooBuildDTO build;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public BambooBuildPlanDTO getPlan() {
        return plan;
    }

    public void setPlan(BambooBuildPlanDTO plan) {
        this.plan = plan;
    }

    public BambooBuildDTO getBuild() {
        return build;
    }

    public void setBuild(BambooBuildDTO build) {
        this.build = build;
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return getBuild().getBuildCompletedDate();
    }

    @Override
    public Optional<String> getCommitHashFromAssignmentRepo() {
        return getCommitHashFromRepo(ASSIGNMENT_REPO_NAME);
    }

    @Override
    public Optional<String> getCommitHashFromTestsRepo() {
        return getCommitHashFromRepo(TEST_REPO_NAME);
    }

    @Override
    public boolean isBuildSuccessful() {
        return getBuild().isSuccessful();
    }

    @Override
    public Double getBuildScore() {
        // the real score is calculated in the grading service
        return 0D;
    }

    @Override
    public String getTestsPassedString() {
        if ("No tests found".equals(getBuild().getTestSummary().getDescription())) {
            return "No tests found";
        }

        int total = getBuild().getTestSummary().getTotalCount();
        int passed = getBuild().getTestSummary().getSuccessfulCount();
        return String.format("%d of %d passed", passed, total);
    }

    @Override
    public boolean hasArtifact() {
        return getBuild().isArtifact();
    }

    @Override
    public boolean hasLogs() {
        return getBuild().getJobs().stream().anyMatch(job -> !job.getLogs().isEmpty());
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs(ProgrammingLanguage programmingLanguage) {
        List<BuildLogEntry> buildLogEntries = new ArrayList<>();

        // Store logs into database. Append logs of multiple jobs.
        for (var job : getBuild().getJobs()) {
            for (var bambooLog : job.getLogs()) {
                // We have to unescape the HTML as otherwise symbols like '<' are not displayed correctly
                buildLogEntries.add(new BuildLogEntry(bambooLog.getDate(), StringEscapeUtils.unescapeHtml(bambooLog.getLog())));
            }
        }

        extractBuildLogStatistics(programmingSubmission, buildLogEntries);

        return buildLogEntries;
    }

    private Optional<String> getCommitHashFromRepo(String repoName) {
        var repo = getBuild().getVcs().stream().filter(vcs -> vcs.getRepositoryName().equalsIgnoreCase(repoName)).findFirst();
        return repo.map(BambooVCSDTO::getId);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooBuildDTO {

        private boolean artifact;

        private int number;

        private String reason;

        private ZonedDateTime buildCompletedDate;

        private boolean successful;

        private BambooTestSummaryDTO testSummary;

        private List<BambooVCSDTO> vcs;

        private List<BambooJobDTO> jobs = new ArrayList<>();

        public boolean isArtifact() {
            return artifact;
        }

        public void setArtifact(boolean artifact) {
            this.artifact = artifact;
        }

        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public ZonedDateTime getBuildCompletedDate() {
            return buildCompletedDate;
        }

        public void setBuildCompletedDate(ZonedDateTime buildCompletedDate) {
            this.buildCompletedDate = buildCompletedDate;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public void setSuccessful(boolean successful) {
            this.successful = successful;
        }

        public BambooTestSummaryDTO getTestSummary() {
            return testSummary;
        }

        public void setTestSummary(BambooTestSummaryDTO testSummary) {
            this.testSummary = testSummary;
        }

        public List<BambooVCSDTO> getVcs() {
            return vcs;
        }

        public void setVcs(List<BambooVCSDTO> vcs) {
            this.vcs = vcs;
        }

        public List<BambooJobDTO> getJobs() {
            return jobs;
        }

        public void setJobs(List<BambooJobDTO> jobs) {
            this.jobs = jobs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooTestSummaryDTO {

        // We don't even know what unit this. It doesn't align at all with the value displayed in Bamboo.
        // E.g. we got a value of 246 for an 8 second run?
        private int duration;

        private int ignoreCount;

        private int failedCount;

        private int existingFailedCount;

        private int quarantineCount;

        private int successfulCount;

        private String description;

        private int skippedCount;

        private int fixedCount;

        private int totalCount;

        private int newFailedCount;

        public int getDuration() {
            return duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }

        public int getIgnoreCount() {
            return ignoreCount;
        }

        public void setIgnoreCount(int ignoreCount) {
            this.ignoreCount = ignoreCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public void setFailedCount(int failedCount) {
            this.failedCount = failedCount;
        }

        public int getExistingFailedCount() {
            return existingFailedCount;
        }

        public void setExistingFailedCount(int existingFailedCount) {
            this.existingFailedCount = existingFailedCount;
        }

        public int getQuarantineCount() {
            return quarantineCount;
        }

        public void setQuarantineCount(int quarantineCount) {
            this.quarantineCount = quarantineCount;
        }

        public int getSuccessfulCount() {
            return successfulCount;
        }

        public void setSuccessfulCount(int successfulCount) {
            this.successfulCount = successfulCount;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public void setSkippedCount(int skippedCount) {
            this.skippedCount = skippedCount;
        }

        public int getFixedCount() {
            return fixedCount;
        }

        public void setFixedCount(int fixedCount) {
            this.fixedCount = fixedCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public int getNewFailedCount() {
            return newFailedCount;
        }

        public void setNewFailedCount(int newFailedCount) {
            this.newFailedCount = newFailedCount;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooVCSDTO {

        private String id;

        private String repositoryName;

        private List<BambooCommitDTO> commits = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRepositoryName() {
            return repositoryName;
        }

        public void setRepositoryName(String repositoryName) {
            this.repositoryName = repositoryName;
        }

        public List<BambooCommitDTO> getCommits() {
            return commits;
        }

        public void setCommits(List<BambooCommitDTO> commits) {
            this.commits = commits;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooCommitDTO {

        private String comment;

        private String id;

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooJobDTO {

        private int id;

        private List<BambooTestJobDTO> failedTests = new ArrayList<>();

        private List<BambooTestJobDTO> successfulTests = new ArrayList<>();

        private List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports = new ArrayList<>();

        // For an unknown reason, the deserialization only works with this annotation
        @JsonProperty("testwiseCoverageReport")
        private List<TestwiseCoverageReportDTO> testwiseCoverageReport = new ArrayList<>();

        private List<BambooBuildLogDTO> logs = new ArrayList<>();

        public List<BambooTestJobDTO> getSuccessfulTests() {
            return successfulTests;
        }

        public void setSuccessfulTests(List<BambooTestJobDTO> successfulTests) {
            this.successfulTests = successfulTests;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public List<BambooTestJobDTO> getFailedTests() {
            return failedTests;
        }

        public void setFailedTests(List<BambooTestJobDTO> failedTests) {
            this.failedTests = failedTests;
        }

        public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
            return staticCodeAnalysisReports;
        }

        public void setStaticCodeAnalysisReports(List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
            this.staticCodeAnalysisReports = staticCodeAnalysisReports;
        }

        public List<TestwiseCoverageReportDTO> getTestwiseCoverageReports() {
            return testwiseCoverageReport;
        }

        public void setTestwiseCoverageReports(List<TestwiseCoverageReportDTO> testwiseCoverageReport) {
            this.testwiseCoverageReport = testwiseCoverageReport;
        }

        public List<BambooBuildLogDTO> getLogs() {
            return logs;
        }

        public void setLogs(List<BambooBuildLogDTO> logs) {
            this.logs = logs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooTestJobDTO {

        private String name;

        private String methodName;

        private String className;

        private List<String> errors = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }
}
