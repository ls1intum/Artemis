package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestSuiteDTO(String name, double time, int errors, int skipped, int failures, int tests, List<TestCaseDTO> testCases) {
}
