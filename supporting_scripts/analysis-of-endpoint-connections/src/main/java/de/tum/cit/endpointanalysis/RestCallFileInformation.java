package de.tum.cit.endpointanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RestCallFileInformation {

    String fileName;
    RestCallInformation[] restCalls;

    public RestCallFileInformation() {

    }

    public RestCallFileInformation(String fileName, RestCallInformation[] restCalls) {
        this.fileName = fileName;
        this.restCalls = restCalls;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public RestCallInformation[] getRestCalls() {
        return restCalls;
    }

    public void setRestCalls(RestCallInformation[] restCalls) {
        this.restCalls = restCalls;
    }
}
