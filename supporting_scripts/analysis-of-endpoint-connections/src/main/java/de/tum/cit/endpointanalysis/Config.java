package de.tum.cit.endpointanalysis;

import java.util.List;

public record Config(List<String> excludedEndpointFiles, List<String> excludedEndpoints, List<ExcludedEndpointOrRestCall> endpointsParsedIncorrectly,
                     List<String> excludedRestCallFiles, List<String> excludedRestCalls, List<ExcludedEndpointOrRestCall> restCallsParsedIncorrectly, String endpointParsingResultPath,
                     String restCallParsingResultPath, String endpointAnalysisResultPath, String restCallAnalysisResultPath, String clientDirPath) {

    static record ExcludedEndpointOrRestCall(String file, int line) {
    }
}
