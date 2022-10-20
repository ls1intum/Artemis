package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestSuiteDTO(String name, double time, int errors, int skipped, int failures, int tests, List<TestCaseDTO> testCases) implements BuildJobDTOInterface {

    // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
    @JsonCreator
    public TestSuiteDTO(String name, double time, int errors, int skipped, int failures, int tests,
            @JsonProperty("testCases") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDTO> testCases) {
        this.name = name;
        this.time = time;
        this.errors = errors;
        this.skipped = skipped;
        this.failures = failures;
        this.tests = tests;
        this.testCases = testCases;
    }

    @Override
    public List<TestCaseDTOInterface> getFailedTests() {
        return testCases.stream().filter(testCase -> !testCase.isSuccessful()).map(testCase -> (TestCaseDTOInterface) testCase).collect(Collectors.toList());
    }

    @Override
    public List<TestCaseDTOInterface> getSuccessfulTests() {
        return testCases.stream().filter(TestCaseDTO::isSuccessful).collect(Collectors.toList());
    }
}
