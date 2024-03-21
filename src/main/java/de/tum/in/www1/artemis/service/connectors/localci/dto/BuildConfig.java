package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;
import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

public record BuildConfig(String buildScript, String dockerImage, String commitHash, String branch, ProgrammingLanguage programmingLanguage, ProjectType projectType,
        boolean scaEnabled, boolean sequentialTestRunsEnabled, boolean testwiseCoverageEnabled, List<String> resultPaths) implements Serializable {
}
