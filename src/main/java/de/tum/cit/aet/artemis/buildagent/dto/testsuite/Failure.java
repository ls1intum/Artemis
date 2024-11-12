package de.tum.cit.aet.artemis.buildagent.dto.testsuite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

// Due to issues with Jackson this currently cannot be a record.
// See https://github.com/FasterXML/jackson-module-kotlin/issues/138#issuecomment-1062725140
@JsonIgnoreProperties(ignoreUnknown = true)
public class Failure {

    String message;

    String detailedMessage;

    public String extractMessage() {
        if (message != null) {
            return message;
        }
        else if (detailedMessage != null) {
            return detailedMessage;
        }
        // empty text nodes are deserialized as null instead of a string, see: https://github.com/FasterXML/jackson-dataformat-xml/issues/565
        // note that this workaround does not fix the issue entirely, as strings of only whitespace become the empty string
        return "";
    }

    @JacksonXmlProperty(isAttribute = true, localName = "message")
    public void setMessage(String message) {
        this.message = message;
    }

    @JacksonXmlText
    public void setDetailedMessage(String detailedMessage) {
        this.detailedMessage = detailedMessage;
    }
}
