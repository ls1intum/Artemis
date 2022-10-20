package de.tum.in.www1.artemis.service.dto;

import java.util.List;

public interface BuildJobDTOInterface {

    List<TestCaseDTOInterface> getFailedTests();

    List<TestCaseDTOInterface> getSuccessfulTests();
}
