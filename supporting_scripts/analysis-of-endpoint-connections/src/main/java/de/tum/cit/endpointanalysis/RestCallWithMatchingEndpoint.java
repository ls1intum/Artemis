package de.tum.cit.endpointanalysis;

public record RestCallWithMatchingEndpoint(EndpointInformation matchingEndpoint, RestCallInformation restCallInformation, String filePath) {
}
