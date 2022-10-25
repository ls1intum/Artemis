package de.tum.in.www1.artemis.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface BuildJobDTOInterface {

    @JsonIgnore
    List<TestCaseDTOInterface> getFailedTests();

    @JsonIgnore
    List<TestCaseDTOInterface> getSuccessfulTests();
}
