package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;

class TestResultXmlParser {

    static void processTestResultFile(String testResultFileString, List<LocalCIBuildResult.LocalCITestJobDTO> failedTests,
            List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests) throws IOException {
        TestSuite testSuite = new XmlMapper().readValue(testResultFileString, TestSuite.class);

        for (TestCase testCase : testSuite.testCases()) {
            Failure failure = testCase.extractFailure();
            if (failure != null) {
                failedTests.add(new LocalCIBuildResult.LocalCITestJobDTO(testCase.name(), List.of(failure.extractMessage())));
            }
            else {
                successfulTests.add(new LocalCIBuildResult.LocalCITestJobDTO(testCase.name(), List.of()));
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestSuite(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testcase") List<TestCase> testCases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestCase(@JacksonXmlProperty(isAttribute = true, localName = "name") String name, @JacksonXmlProperty(localName = "failure") Failure failure,
            @JacksonXmlProperty(localName = "error") Failure error) {

        private Failure extractFailure() {
            return failure != null ? failure : error;
        }
    }

    // Due to issues with Jackson this currently cannot be a record
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Failure {

        private String message;

        private String detailedMessage;

        public Failure() {
        }

        private String extractMessage() {
            return message != null ? message : detailedMessage;
        }

        @JacksonXmlText
        public void setDetailedMessage(String detailedMessage) {
            this.detailedMessage = detailedMessage;
        }

        @JacksonXmlProperty(isAttribute = true, localName = "message")
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
