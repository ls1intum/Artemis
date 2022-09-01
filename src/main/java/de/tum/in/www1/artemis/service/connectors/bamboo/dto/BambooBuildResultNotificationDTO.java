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
// Note: due to limitations with inheritance, we cannot declare this as record, but we can use it in a similar way with final fields
public class BambooBuildResultNotificationDTO extends AbstractBuildResultNotificationDTO {

    private final String secret;

    private final String notificationType;

    private final BambooBuildPlanDTO plan;

    private final BambooBuildDTO build;

    public BambooBuildResultNotificationDTO(String secret, String notificationType, BambooBuildPlanDTO plan, BambooBuildDTO build) {
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
                buildLogEntries.add(new BuildLogEntry(bambooLog.date(), StringEscapeUtils.unescapeHtml(bambooLog.log())));
            }
        }

        return buildLogEntries;
    }

    private Optional<String> getCommitHashFromRepo(String repoName) {
        var repo = getBuild().vcs().stream().filter(vcs -> vcs.repositoryName().equalsIgnoreCase(repoName)).findFirst();
        return repo.map(BambooVCSDTO::id);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooBuildDTO(boolean artifact, int number, String reason, ZonedDateTime buildCompletedDate, boolean successful, BambooTestSummaryDTO testSummary,
            List<BambooVCSDTO> vcs, List<BambooJobDTO> jobs) {
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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooCommitDTO(String comment, String id) {
    }

    /**
     * @param testwiseCoverageReport  For an unknown reason, the deserialization only works with this annotation */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooJobDTO(int id, List<BambooTestJobDTO> failedTests, List<BambooTestJobDTO> successfulTests, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports,
            @JsonProperty("testwiseCoverageReport") List<TestwiseCoverageReportDTO> testwiseCoverageReport, List<BambooBuildLogDTO> logs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooTestJobDTO(String name, String methodName, String className, List<String> errors) {
    }
}
