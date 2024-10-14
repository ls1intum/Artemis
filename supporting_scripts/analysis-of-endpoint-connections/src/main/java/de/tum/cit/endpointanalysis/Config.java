package de.tum.cit.endpointanalysis;

import java.util.List;

public record Config(List<String> excludedEndpointFiles, List<String> excludedEndpoints, List<String> excludedRestCallFiles,
        List<String> excludedRestCalls, String endpointParsingResultPath, String restCallParsingResultPath, String endpointAnalysisResultPath, String restCallAnalysisResultPath,
        String clientDirPath) {
}
