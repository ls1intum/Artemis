package de.tum.cit.aet.artemis.buildagent.dto.testsuite;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestSuite(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testcase") List<TestCase> testCases,
        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testsuite") List<TestSuite> testSuites) {

    public TestSuite {
        testCases = Objects.requireNonNullElse(testCases, Collections.emptyList());
        testSuites = Objects.requireNonNullElse(testSuites, Collections.emptyList());
    }
}
