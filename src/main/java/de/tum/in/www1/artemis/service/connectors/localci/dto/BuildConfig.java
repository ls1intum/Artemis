package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildConfig(String buildScript, String dockerImage, String commitHash, String branch, ProgrammingLanguage programmingLanguage, ProjectType projectType,
        boolean scaEnabled, boolean sequentialTestRunsEnabled, boolean testwiseCoverageEnabled, List<String> resultPaths) implements Serializable {

    @Override
    public String dockerImage() {
        // make sure to avoid whitespace issues
        return dockerImage.trim();
    }
}
