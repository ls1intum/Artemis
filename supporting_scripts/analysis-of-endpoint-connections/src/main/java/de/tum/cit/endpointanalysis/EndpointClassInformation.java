package de.tum.cit.endpointanalysis;

import java.util.List;

public class EndpointClassInformation {

    private String filePath;

    private String classRequestMapping;

    private List<EndpointInformation> endpoints;

    public EndpointClassInformation() {

    }

    public EndpointClassInformation(String filePath, String classRequestMapping, List<EndpointInformation> endpoints) {
        this.filePath = filePath;
        this.classRequestMapping = classRequestMapping;
        this.endpoints = endpoints;
    }

    public String getClassRequestMapping() {
        return classRequestMapping;
    }

    public void setClassRequestMapping(String classRequestMapping) {
        this.classRequestMapping = classRequestMapping;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<EndpointInformation> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointInformation> endpoints) {
        this.endpoints = endpoints;
    }
}
