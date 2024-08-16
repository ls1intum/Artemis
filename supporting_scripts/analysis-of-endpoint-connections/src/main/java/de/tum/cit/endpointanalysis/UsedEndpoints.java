package de.tum.cit.endpointanalysis;

import java.util.List;

public record UsedEndpoints(EndpointInformation endpointInformation, List<RestCallInformation> matchingRestCalls, String filePath) {
}
