package de.tum.cit.endpointanalysis;

import java.util.List;

public record RestCallFileInformation(String filePath, List<RestCallInformation> restCalls) {
}
