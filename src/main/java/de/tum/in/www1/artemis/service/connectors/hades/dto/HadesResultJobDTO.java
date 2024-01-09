package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

public class HadesResultJobDTO implements BuildJobDTOInterface {

    @JsonProperty("name")
    private String name;

    @JsonProperty("package")
    private String packageName;

    @JsonProperty("tests")
    private List<HadesTestCaseResultDTO> tests;

    // empty constructor needed for Jackson
    public HadesResultJobDTO() {
    }

    @Override
    public List<? extends TestCaseDTOInterface> getFailedTests() {
        return tests.stream().filter(test -> !test.isSuccessful()).toList();
    }

    @Override
    public List<? extends TestCaseDTOInterface> getSuccessfulTests() {
        return tests.stream().filter(HadesTestCaseResultDTO::isSuccessful).toList();
    }

}
