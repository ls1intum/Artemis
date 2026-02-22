package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Represents all the information returned by the local CI system about a build.
 * Note: due to limitations with inheritance, we cannot declare this as a record, but we can use it in a similar way with final fields.
 */

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isCompilationSuccessful, ZonedDateTime buildRunDate,
        List<LocalCIJobDTO> jobs, List<BuildLogDTO> buildLogEntries, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports, boolean hasLogs)
        implements BuildResultNotification, Serializable {

    public BuildResult {
        buildRunDate = Objects.requireNonNullElse(buildRunDate, ZonedDateTime.now());
        jobs = Objects.requireNonNullElse(jobs, new ArrayList<>());
        staticCodeAnalysisReports = Objects.requireNonNullElse(staticCodeAnalysisReports, new ArrayList<>());
        buildLogEntries = Objects.requireNonNullElse(buildLogEntries, new ArrayList<>());
        hasLogs = !buildLogEntries.isEmpty();
    }

    public BuildResult(String branch, String assignmentRepoCommitHash, String testsRepoCommitHash, List<BuildLogDTO> buildLogs, boolean isCompilationSuccessful) {
        this(branch, assignmentRepoCommitHash, testsRepoCommitHash, isCompilationSuccessful, null, null, buildLogs, null, buildLogs != null && !buildLogs.isEmpty());
    }

    @Override
    public Double buildScore() {
        // the real score is calculated in the grading service
        return 0D;
    }

    /**
     * NOTE: Local CI does not support checking for artifacts as of now.
     *
     * @return will always return false because LocalCI does not support checking for artifacts.
     */
    @Override
    public boolean hasArtifact() {
        return false;
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs() {
        // convert the buildLogEntry DTOs to BuildLogEntry objects
        return buildLogEntries.stream().map(log -> new BuildLogEntry(log.time(), log.log())).toList();
    }
}
