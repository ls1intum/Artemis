package de.tum.in.www1.artemis;

public class RestCallWithMatchingEndpoint {
    private EndpointInformation matchingEndpoint;
    private RestCallInformation restCallInformation;
    private String filePath;

    public RestCallWithMatchingEndpoint(EndpointInformation matchingEndpoint, RestCallInformation restCallInformation, String filePath) {
        this.matchingEndpoint = matchingEndpoint;
        this.restCallInformation = restCallInformation;
        this.filePath = filePath;
    }

    public RestCallWithMatchingEndpoint() {
    }

    public EndpointInformation getMatchingEndpoint() {
        return matchingEndpoint;
    }

    public void setMatchingEndpoint(EndpointInformation matchingEndpoint) {
        this.matchingEndpoint = matchingEndpoint;
    }

    public RestCallInformation getRestCallInformation() {
        return restCallInformation;
    }

    public void setRestCallInformation(RestCallInformation restCallInformation) {
        this.restCallInformation = restCallInformation;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
