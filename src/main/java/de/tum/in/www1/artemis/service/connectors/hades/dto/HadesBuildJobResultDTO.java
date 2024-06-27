package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.TestCaseBaseDTO;

public record HadesBuildJobResultDTO(@JsonProperty("name") String name, @JsonProperty("package") String packageName, @JsonProperty("tests") List<HadesTestCaseResultDTO> tests)
        implements BuildJobDTOInterface {

    @Override
    public List<? extends TestCaseBaseDTO> getFailedTests() {
        return tests.stream().filter(test -> !test.isSuccessful()).toList();
    }

    @Override
    public List<? extends TestCaseBaseDTO> getSuccessfulTests() {
        return tests.stream().filter(HadesTestCaseResultDTO::isSuccessful).toList();
    }
}
