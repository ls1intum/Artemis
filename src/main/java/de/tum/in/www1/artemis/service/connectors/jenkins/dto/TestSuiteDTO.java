package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestSuiteDTO(String name, double time, int errors, int skipped, int failures, int tests, List<TestCaseDTO> testCases) {

    // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
    @JsonCreator
    public TestSuiteDTO(String name, double time, int errors, int skipped, int failures, int tests, @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDTO> testCases) {
        this.name = name;
        this.time = time;
        this.errors = errors;
        this.skipped = skipped;
        this.failures = failures;
        this.tests = tests;
        this.testCases = testCases;
    }
}
