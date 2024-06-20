package de.tum.cit.endpointanalysis;

import java.util.List;

public class EndpointAnalysis {
    private List<UsedEndpoints> usedEndpoints;
    private List<EndpointInformation> unusedEndpoints;

    public EndpointAnalysis(List<UsedEndpoints> usedEndpoints, List<EndpointInformation> unusedEndpoints) {
        this.usedEndpoints = usedEndpoints;
        this.unusedEndpoints = unusedEndpoints;
    }

    public EndpointAnalysis() {
    }

    public List<UsedEndpoints> getUsedEndpoints() {
        return usedEndpoints;
    }

    public void setUsedEndpoints(List<UsedEndpoints> usedEndpoints) {
        this.usedEndpoints = usedEndpoints;
    }

    public List<EndpointInformation> getUnusedEndpoints() {
        return unusedEndpoints;
    }

    public void setUnusedEndpoints(List<EndpointInformation> unusedEndpoints) {
        this.unusedEndpoints = unusedEndpoints;
    }
}
