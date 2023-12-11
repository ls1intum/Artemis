package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

public class HadesCIBuildResultDTO extends AbstractBuildResultNotificationDTO {

    private final String assignmentRepoBranchName;

    private final String assignmentRepoCommitHash;

    private final String testsRepoCommitHash;

    private final boolean isBuildSuccessful;

    private final ZonedDateTime buildRunDate;

    // Missing

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
        // TODO: implement for Hades
        return false;
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
    public List<BuildLogEntry> extractBuildLogs(ProgrammingLanguage programmingLanguage) {
        return null;
    }

    @Override
    public List<? extends BuildJobDTOInterface> getBuildJobs() {
        return null;
    }

    @Override
    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return null;
    }

    @Override
    public List<TestwiseCoverageReportDTO> getTestwiseCoverageReports() {
        return null;
    }
}
