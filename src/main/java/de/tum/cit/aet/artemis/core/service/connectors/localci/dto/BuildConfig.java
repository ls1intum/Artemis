package de.tum.cit.aet.artemis.core.service.connectors.localci.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildConfig(String buildScript, String dockerImage, String commitHashToBuild, String assignmentCommitHash, String testCommitHash, String branch,
        ProgrammingLanguage programmingLanguage, ProjectType projectType, boolean scaEnabled, boolean sequentialTestRunsEnabled, boolean testwiseCoverageEnabled,
        List<String> resultPaths) implements Serializable {

    @Override
    public String dockerImage() {
        // make sure to avoid whitespace issues
        return dockerImage.trim();
    }
}
