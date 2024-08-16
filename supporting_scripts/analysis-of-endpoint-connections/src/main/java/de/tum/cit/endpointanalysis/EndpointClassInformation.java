package de.tum.cit.endpointanalysis;

import java.util.List;

public record EndpointClassInformation(String filePath, String classRequestMapping, List<EndpointInformation> endpoints) {
}
