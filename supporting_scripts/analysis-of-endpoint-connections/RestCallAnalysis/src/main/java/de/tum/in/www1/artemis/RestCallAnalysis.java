package de.tum.in.www1.artemis;

import java.util.List;

public class RestCallAnalysis {

    private List<RestCallWithMatchingEndpoint> restCallsWithMatchingEndpoints;

    private List<RestCallInformation> restCallsWithoutMatchingEndpoints;

    public RestCallAnalysis(List<RestCallWithMatchingEndpoint> restCallsWithMatchingEndpoints, List<RestCallInformation> restCallsWithoutMatchingEndpoints) {
        this.restCallsWithMatchingEndpoints = restCallsWithMatchingEndpoints;
        this.restCallsWithoutMatchingEndpoints = restCallsWithoutMatchingEndpoints;
    }

    public RestCallAnalysis() {
    }

    public List<RestCallWithMatchingEndpoint> getRestCallsWithMatchingEndpoints() {
        return restCallsWithMatchingEndpoints;
    }

    public void setRestCallsWithMatchingEndpoints(List<RestCallWithMatchingEndpoint> restCallsWithMatchingEndpoints) {
        this.restCallsWithMatchingEndpoints = restCallsWithMatchingEndpoints;
    }

    public List<RestCallInformation> getRestCallsWithoutMatchingEndpoints() {
        return restCallsWithoutMatchingEndpoints;
    }

    public void setRestCallsWithoutMatchingEndpoints(List<RestCallInformation> restCallsWithoutMatchingEndpoints) {
        this.restCallsWithoutMatchingEndpoints = restCallsWithoutMatchingEndpoints;
    }
}
