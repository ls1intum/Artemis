package de.tum.in.www1.artemis;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EndpointClassInformation {

    @JsonProperty
    private String fileName;

    @JsonProperty
    private String classRequestMapping;

    @JsonProperty
    private List<EndpointInformation> endpoints;

    public EndpointClassInformation() {

    }

    public EndpointClassInformation(String fileName, String classRequestMapping, List<EndpointInformation> endpoints) {
        this.fileName = fileName;
        this.classRequestMapping = classRequestMapping;
        this.endpoints = endpoints;
    }

    public String getClassRequestMapping() {
        return classRequestMapping;
    }

    public void setClassRequestMapping(String classRequestMapping) {
        this.classRequestMapping = classRequestMapping;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<EndpointInformation> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointInformation> endpoints) {
        this.endpoints = endpoints;
    }
}
