package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;

import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Deprecated(forRemoval = true) // will be removed in 7.0.0
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
    protected String getCommitHashFromAssignmentRepo() {
        return getCommitHashFromRepo(ASSIGNMENT_REPO_NAME);
    }

    @Override
    protected String getCommitHashFromTestsRepo() {
        return getCommitHashFromRepo(TEST_REPO_NAME);
    }

    @Override
    public String getBranchNameFromAssignmentRepo() {
        var repo = getBuild().vcs().stream().filter(vcs -> vcs.repositoryName().equalsIgnoreCase(ASSIGNMENT_REPO_NAME)).findFirst();
        return repo.map(BambooVCSDTO::branchName).orElse(null);
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
    public List<BuildLogEntry> extractBuildLogs() {
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
    public List<? extends BuildJobDTOInterface> getBuildJobs() {
        return (List<BuildJobDTOInterface>) (List<?>) getBuild().jobs();
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

    @Nullable
    private String getCommitHashFromRepo(String repoName) {
        var repo = getBuild().vcs().stream().filter(vcs -> vcs.repositoryName().equalsIgnoreCase(repoName)).findFirst();
        return repo.map(BambooVCSDTO::id).orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooBuildDTO(boolean artifact, int number, String reason, ZonedDateTime buildCompletedDate, boolean successful, BambooTestSummaryDTO testSummary,
            @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooVCSDTO> vcs, @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooJobDTO> jobs) {
    }

    /**
     * @param duration We don't even know what unit this. It doesn't align at all with the value displayed in Bamboo. E.g. we got a value of 246 for an 8 second run?
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooTestSummaryDTO(int duration, int ignoreCount, int failedCount, int existingFailedCount, int quarantineCount, int successfulCount, String description,
            int skippedCount, int fixedCount, int totalCount, int newFailedCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooVCSDTO(String id, String repositoryName, String branchName, @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooCommitDTO> commits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooCommitDTO(String comment, String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooJobDTO(int id, @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooTestJobDTO> failedTests,
            @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooTestJobDTO> successfulTests,
            @JsonSetter(nulls = Nulls.AS_EMPTY) List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports,
            @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestwiseCoverageReportDTO> testwiseCoverageReport, @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooBuildLogDTO> logs)
            implements BuildJobDTOInterface {

        @Override
        public List<? extends TestCaseDTOInterface> getFailedTests() {
            return failedTests;
        }

        @Override
        public List<? extends TestCaseDTOInterface> getSuccessfulTests() {
            return successfulTests;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooTestJobDTO(String name, String methodName, String className, @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> errors) implements TestCaseDTOInterface {

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
