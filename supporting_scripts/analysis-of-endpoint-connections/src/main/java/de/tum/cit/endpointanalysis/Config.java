package de.tum.cit.endpointanalysis;

import java.util.List;

public record Config(List<String> excludedEndpointFiles, List<String> excludedEndpoints, List<ExcludedEnpointOrRestCall> endpointsParsedIncorrectly,
        List<String> excludedRestCallFiles, List<String> excludedRestCalls, List<ExcludedEnpointOrRestCall> restCallsParsedIncorrectly, String endpointParsingResultPath,
        String restCallParsingResultPath, String endpointAnalysisResultPath, String restCallAnalysisResultPath, String clientDirPath) {

    static record ExcludedEnpointOrRestCall(String file, int line) {
    }
}
