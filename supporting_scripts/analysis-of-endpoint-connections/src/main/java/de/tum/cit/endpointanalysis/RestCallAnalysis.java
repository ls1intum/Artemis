package de.tum.cit.endpointanalysis;

import java.util.List;

public record RestCallAnalysis(List<RestCallWithMatchingEndpoint> restCallsWithMatchingEndpoints, List<RestCallInformation> restCallsWithoutMatchingEndpoints) {
}
