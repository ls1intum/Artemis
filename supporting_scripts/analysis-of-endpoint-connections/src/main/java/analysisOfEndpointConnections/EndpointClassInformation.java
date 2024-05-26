package analysisOfEndpointConnections;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class EndpointClassInformation {

    @JsonProperty
    private String className;

    @JsonProperty
    private String classRequestMapping;

    @JsonProperty
    private List<EnpointInformation> endpoints;

    public EndpointClassInformation(String className, String classRequestMapping, List<EnpointInformation> endpoints) {
        this.className = className;
        this.classRequestMapping = classRequestMapping;
        this.endpoints = endpoints;
    }
}
