package de.tum.cit.aet.artemis.buildagent.dto.testsuite;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestSuites(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testsuite") List<TestSuite> testSuites) {

    public TestSuites {
        testSuites = Objects.requireNonNullElse(testSuites, Collections.emptyList());
    }
}
