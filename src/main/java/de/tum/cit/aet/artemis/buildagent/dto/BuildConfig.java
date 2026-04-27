package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
// TODO: reduce the amount of parameters and combine some in smaller record DTOs
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildConfig(String buildScript, String dockerImage, String commitHashToBuild, String assignmentCommitHash, String testCommitHash, String branch,
        ProgrammingLanguage programmingLanguage, ProjectType projectType, boolean scaEnabled, boolean sequentialTestRunsEnabled, List<String> resultPaths, int timeoutSeconds,
        String assignmentCheckoutPath, String testCheckoutPath, String solutionCheckoutPath, DockerRunConfig dockerRunConfig) implements Serializable {

    @Override
    public String dockerImage() {
        // make sure to avoid whitespace issues
        return dockerImage.trim();
    }

    public boolean areTestsExpected() {
        if (resultPaths == null || resultPaths.isEmpty()) {
            return false;
        }
        return hasNonStaticCodeAnalysisResultPath();
    }

    private boolean hasNonStaticCodeAnalysisResultPath() {
        Stream<String> fileNames = resultPaths.stream().map(path -> Path.of(path).getFileName().toString());
        return fileNames.anyMatch(fileName -> StaticCodeAnalysisTool.getToolByFilePattern(fileName).isEmpty());
    }
}
