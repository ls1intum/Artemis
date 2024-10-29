package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildConfig(String buildScript, String dockerImage, String commitHashToBuild, String assignmentCommitHash, String testCommitHash, String branch,
        ProgrammingLanguage programmingLanguage, ProjectType projectType, boolean scaEnabled, boolean sequentialTestRunsEnabled, boolean testwiseCoverageEnabled,
        List<String> resultPaths, int timeoutSeconds, String assignmentCheckoutPath, String testCheckoutPath, String solutionCheckoutPath) implements Serializable {

    @Override
    public String dockerImage() {
        // make sure to avoid whitespace issues
        return dockerImage.trim();
    }
}
