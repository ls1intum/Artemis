package de.tum.in.www1.artemis;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RestCallFileInformation {

    @JsonProperty
    String fileName;

    @JsonProperty
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
