package de.tum.cit.endpointanalysis;

import java.util.List;

public record EndpointAnalysis(List<UsedEndpoints> usedEndpoints, List<EndpointInformation> unusedEndpoints) {
}
