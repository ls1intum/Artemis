package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: due to limitations with inheritance, we cannot declare this as record, but we can use it in a similar way with final fields
public class BambooBuildResultNotificationDTO extends AbstractBuildResultNotificationDTO {

    private final String secret;

    private final String notificationType;

    private final BambooBuildPlanDTO plan;

    private final BambooBuildDTO build;

    @JsonCreator
    public BambooBuildResultNotificationDTO(@JsonProperty("secret") String secret, @JsonProperty("notificationType") String notificationType,
            @JsonProperty("plan") BambooBuildPlanDTO plan, @JsonProperty("build") BambooBuildDTO build) {
        this.secret = secret;
        this.notificationType = notificationType;
        this.plan = plan;
        this.build = build;
    }

    public String getSecret() {
        return secret;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public BambooBuildPlanDTO getPlan() {
        return plan;
    }

    public BambooBuildDTO getBuild() {
        return build;
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return getBuild().buildCompletedDate();
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
    public Optional<String> getBranchNameFromAssignmentRepo() {
        var repo = getBuild().vcs().stream().filter(vcs -> vcs.repositoryName().equalsIgnoreCase(ASSIGNMENT_REPO_NAME)).findFirst();
        return repo.map(BambooVCSDTO::branchName);
    }

    @Override
    public boolean isBuildSuccessful() {
        return getBuild().successful();
    }

    @Override
    public Double getBuildScore() {
        // the real score is calculated in the grading service
        return 0D;
    }

    @Override
    public boolean hasArtifact() {
        return getBuild().artifact();
    }

    @Override
    public boolean hasLogs() {
        return getBuild().jobs().stream().anyMatch(job -> !job.logs().isEmpty());
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs(ProgrammingLanguage programmingLanguage) {
        List<BuildLogEntry> buildLogEntries = new ArrayList<>();

        // Store logs into database. Append logs of multiple jobs.
        for (var job : getBuild().jobs()) {
            for (var bambooLog : job.logs()) {
                // We have to unescape the HTML as otherwise symbols like '<' are not displayed correctly
                buildLogEntries.add(new BuildLogEntry(bambooLog.date(), StringEscapeUtils.unescapeHtml4(bambooLog.log())));
            }
        }

        return buildLogEntries;
    }

    @JsonIgnore
    @Override
    public List<BuildJobDTOInterface> getBuildJobs() {
        return getBuild().jobs().stream().map(BuildJobDTOInterface.class::cast).toList();
    }

    @JsonIgnore
    @Override
    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return getBuild().jobs().stream().flatMap(job -> job.staticCodeAnalysisReports().stream()).toList();
    }

    @JsonIgnore
    @Override
    public List<TestwiseCoverageReportDTO> getTestwiseCoverageReports() {
        return getBuild().jobs().stream().flatMap(job -> job.testwiseCoverageReport().stream()).toList();
    }

    private Optional<String> getCommitHashFromRepo(String repoName) {
        var repo = getBuild().vcs().stream().filter(vcs -> vcs.repositoryName().equalsIgnoreCase(repoName)).findFirst();
        return repo.map(BambooVCSDTO::id);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooBuildDTO(boolean artifact, int number, String reason, ZonedDateTime buildCompletedDate, boolean successful, BambooTestSummaryDTO testSummary,
            List<BambooVCSDTO> vcs, List<BambooJobDTO> jobs) {

        // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
        @JsonCreator
        public BambooBuildDTO(boolean artifact, int number, String reason, ZonedDateTime buildCompletedDate, boolean successful, BambooTestSummaryDTO testSummary,
                @JsonProperty("vcs") @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooVCSDTO> vcs,
                @JsonProperty("jobs") @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooJobDTO> jobs) {
            this.artifact = artifact;
            this.number = number;
            this.reason = reason;
            this.buildCompletedDate = buildCompletedDate;
            this.successful = successful;
            this.testSummary = testSummary;
            this.vcs = vcs;
            this.jobs = jobs;
        }
    }

    /**
     * @param duration  We don't even know what unit this. It doesn't align at all with the value displayed in Bamboo. E.g. we got a value of 246 for an 8 second run? */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooTestSummaryDTO(int duration, int ignoreCount, int failedCount, int existingFailedCount, int quarantineCount, int successfulCount, String description,
            int skippedCount, int fixedCount, int totalCount, int newFailedCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooVCSDTO(String id, String repositoryName, String branchName, List<BambooCommitDTO> commits) {

        // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
        @JsonCreator
        public BambooVCSDTO(String id, String repositoryName, String branchName, @JsonProperty("commits") @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooCommitDTO> commits) {
            this.id = id;
            this.repositoryName = repositoryName;
            this.branchName = branchName;
            this.commits = commits;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooCommitDTO(String comment, String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooJobDTO(int id, List<BambooTestJobDTO> failedTests, List<BambooTestJobDTO> successfulTests, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports,
            List<TestwiseCoverageReportDTO> testwiseCoverageReport, List<BambooBuildLogDTO> logs) implements BuildJobDTOInterface {

        // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
        @JsonCreator
        public BambooJobDTO(int id, @JsonProperty("failedTests") @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooTestJobDTO> failedTests,
                @JsonProperty("successfulTests") @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooTestJobDTO> successfulTests,
                @JsonProperty("staticCodeAnalysisReports") @JsonSetter(nulls = Nulls.AS_EMPTY) List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports,
                @JsonProperty("testwiseCoverageReport") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestwiseCoverageReportDTO> testwiseCoverageReport,
                @JsonProperty("logs") @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooBuildLogDTO> logs) {
            this.id = id;
            this.failedTests = failedTests;
            this.successfulTests = successfulTests;
            this.staticCodeAnalysisReports = staticCodeAnalysisReports;
            this.testwiseCoverageReport = testwiseCoverageReport;
            this.logs = logs;
        }

        @Override
        public List<TestCaseDTOInterface> getFailedTests() {
            return new ArrayList<>(failedTests);
        }

        @Override
        public List<TestCaseDTOInterface> getSuccessfulTests() {
            return new ArrayList<>(successfulTests);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooTestJobDTO(String name, String methodName, String className, List<String> errors) implements TestCaseDTOInterface {

        // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
        @JsonCreator
        public BambooTestJobDTO(String name, String methodName, String className, @JsonProperty("errors") @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> errors) {
            this.name = name;
            this.methodName = methodName;
            this.className = className;
            this.errors = errors;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getMessage() {
            return errors;
        }
    }
}
