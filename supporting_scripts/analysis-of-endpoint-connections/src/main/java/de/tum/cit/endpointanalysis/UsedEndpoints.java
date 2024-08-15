package de.tum.cit.endpointanalysis;

import java.util.List;

public class UsedEndpoints {

    private EndpointInformation endpointInformation;

    private List<RestCallInformation> matchingRestCalls;

    private String filePath;

    public UsedEndpoints(EndpointInformation endpointInformation, List<RestCallInformation> matchingRestCalls, String filePath) {
        this.endpointInformation = endpointInformation;
        this.matchingRestCalls = matchingRestCalls;
        this.filePath = filePath;
    }

    public UsedEndpoints() {
    }

    public EndpointInformation getEndpointInformation() {
        return endpointInformation;
    }

    public void setEndpointInformation(EndpointInformation endpointInformation) {
        this.endpointInformation = endpointInformation;
    }

    public List<RestCallInformation> getMatchingRestCalls() {
        return matchingRestCalls;
    }

    public void setMatchingRestCalls(List<RestCallInformation> matchingRestCalls) {
        this.matchingRestCalls = matchingRestCalls;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
