package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.annotation.JsonSetter;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
import de.tum.in.www1.artemis.service.dto.*;

public class HadesCIBuildResultDTO extends AbstractBuildResultNotificationDTO {

    private static final Logger log = LoggerFactory.getLogger(HadesCIBuildResultDTO.class);

    private String jobName;

    private String assignmentRepoBranchName;

    private String assignmentRepoCommitHash;

    private String testsRepoCommitHash;

    private boolean isBuildSuccessful;

    // TODO: custom deserializer for ZonedDateTime
    private ZonedDateTime buildRunDate;

    private List<HadesResultJobDTO> buildJobs;

    // empty constructor needed for Jackson
    public HadesCIBuildResultDTO() {
    }

    public HadesCIBuildResultDTO(String jobName, String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful,
            ZonedDateTime buildRunDate, List<HadesResultJobDTO> buildJobs) {
        this.jobName = jobName;
        this.assignmentRepoBranchName = assignmentRepoBranchName;
        this.assignmentRepoCommitHash = assignmentRepoCommitHash;
        this.testsRepoCommitHash = testsRepoCommitHash;
        this.isBuildSuccessful = isBuildSuccessful;
        this.buildRunDate = buildRunDate;
        this.buildJobs = buildJobs;
    }

    public static HadesCIBuildResultDTO convert(Object someResult) {
        var mapper = new ObjectMapper();
        // This is not working at the moment :(
        var dto = mapper.convertValue(someResult, HadesCIBuildResultDTO.class);
        log.debug("Converted {} to {}", someResult, dto);
        return dto;
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return null;
    }

    @Override
    public Optional<String> getCommitHashFromAssignmentRepo() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getCommitHashFromTestsRepo() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getBranchNameFromAssignmentRepo() {
        return Optional.empty();
    }

    @Override
    public boolean isBuildSuccessful() {
        return isBuildSuccessful;
    }

    @Override
    public Double getBuildScore() {
        return null;
    }

    @Override
    public boolean hasArtifact() {
        return false;
    }

    @Override
    public boolean hasLogs() {
        return false;
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs() {
        return null;
    }

    @Override
    public List<? extends BuildJobDTOInterface> getBuildJobs() {
        return buildJobs;
    }

    @Override
    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return null;
    }

    @Override
    public List<TestwiseCoverageReportDTO> getTestwiseCoverageReports() {
        return null;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public void setAssignmentRepoBranchName(String assignmentRepoBranchName) {
        this.assignmentRepoBranchName = assignmentRepoBranchName;
    }

    public void setAssignmentRepoCommitHash(String assignmentRepoCommitHash) {
        this.assignmentRepoCommitHash = assignmentRepoCommitHash;
    }

    public void setTestsRepoCommitHash(String testsRepoCommitHash) {
        this.testsRepoCommitHash = testsRepoCommitHash;
    }

    public void setBuildSuccessful(boolean buildSuccessful) {
        isBuildSuccessful = buildSuccessful;
    }

    public void setBuildRunDate(ZonedDateTime buildRunDate) {
        this.buildRunDate = buildRunDate;
    }

    @JsonSetter("buildJobs")
    public void setBuildJobs(List<HadesResultJobDTO> buildJobs) {
        this.buildJobs = buildJobs;
    }

}
