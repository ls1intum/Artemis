package de.tum.cit.endpointanalysis;

import java.util.ArrayList;
import java.util.List;

public class EndpointAnalysis {

    private final List<UsedEndpoints> usedEndpoints;

    private final List<EndpointInformation> unusedEndpoints;

    public EndpointAnalysis(List<UsedEndpoints> usedEndpoints, List<EndpointInformation> unusedEndpoints) {
        this.usedEndpoints = usedEndpoints;
        this.unusedEndpoints = unusedEndpoints;
    }

    public List<UsedEndpoints> getUsedEndpoints() {
        return usedEndpoints;
    }

    public List<EndpointInformation> getUnusedEndpoints() {
        return unusedEndpoints;
    }
}
