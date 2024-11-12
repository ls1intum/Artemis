package de.tum.cit.aet.artemis.buildagent.dto.testsuite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestCase(@JacksonXmlProperty(isAttribute = true, localName = "name") String name, @JacksonXmlProperty(localName = "failure") Failure failure,
        @JacksonXmlProperty(localName = "error") Failure error, @JacksonXmlProperty(localName = "skipped") Skip skipped, String successMessage) {

    public boolean isSkipped() {
        return skipped != null;
    }

    public Failure extractFailure() {
        return failure != null ? failure : error;
    }
}
