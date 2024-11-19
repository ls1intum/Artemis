package de.tum.cit.aet.artemis.buildagent.service.parser;

import java.util.List;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.Failure;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.TestCase;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.TestSuite;

abstract class AbstractParser {

    protected static void processTestSuite(TestSuite testSuite, List<BuildResult.LocalCITestJobDTO> failedTests, List<BuildResult.LocalCITestJobDTO> successfulTests) {
        for (TestCase testCase : testSuite.testCases()) {
            if (testCase.isSkipped()) {
                continue;
            }
            Failure failure = testCase.extractFailure();
            if (failure != null) {
                failedTests.add(new BuildResult.LocalCITestJobDTO(testCase.name(), List.of(failure.extractMessage())));
            }
            else {
                successfulTests.add(new BuildResult.LocalCITestJobDTO(testCase.name(), List.of(testCase.extractSuccessMessage())));
            }
        }

        for (TestSuite suite : testSuite.testSuites()) {
            processTestSuite(suite, failedTests, successfulTests);
        }
    }
}
