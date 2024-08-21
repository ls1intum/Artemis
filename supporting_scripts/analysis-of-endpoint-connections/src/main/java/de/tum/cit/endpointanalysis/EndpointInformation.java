package de.tum.cit.endpointanalysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record EndpointInformation(String requestMapping, String endpoint, String httpMethodAnnotation, String uri, String className, int line, List<String> otherAnnotations) {

    String buildCompleteEndpointURI() {
        StringBuilder result = new StringBuilder();
        if (this.requestMapping != null && !this.requestMapping.isEmpty()) {
            // Remove quotes from the requestMapping as they are used to define the String in the source code but are not part of the URI
            result.append(this.requestMapping.replace("\"", ""));
        }
        // Remove quotes from the URI as they are used to define the String in the source code but are not part of the URI
        result.append(this.uri.replace("\"", ""));
        return result.toString();
    }

    String buildComparableEndpointUri() {
        // Replace arguments with placeholder
        return this.buildCompleteEndpointURI().replaceAll("\\{.*?\\}", ":param:");
    }

    @JsonIgnore
    String getHttpMethod() {
        return switch (this.httpMethodAnnotation) {
            case "GetMapping" -> "get";
            case "PostMapping" -> "post";
            case "PutMapping" -> "put";
            case "DeleteMapping" -> "delete";
            case "PatchMapping" -> "patch";
            default -> "No HTTP method annotation found";
        };
    }
}
