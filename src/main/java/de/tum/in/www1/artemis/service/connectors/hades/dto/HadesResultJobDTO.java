package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.util.List;

import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

public class HadesResultJobDTO implements BuildJobDTOInterface {

    private List<HadesTestCaseResultDTO> results;

    // empty constructor needed for Jackson
    public HadesResultJobDTO() {
    }

    public HadesResultJobDTO(List<HadesTestCaseResultDTO> results) {
        this.results = results;
    }

    @Override
    public List<? extends TestCaseDTOInterface> getFailedTests() {
        return results.stream().filter(result -> !result.isSuccessful()).toList();
    }

    @Override
    public List<? extends TestCaseDTOInterface> getSuccessfulTests() {
        return results.stream().filter(result -> !result.isSuccessful()).toList();
    }

    public List<HadesTestCaseResultDTO> getResults() {
        return results;
    }

    public void setResults(List<HadesTestCaseResultDTO> results) {
        this.results = results;
    }
}
