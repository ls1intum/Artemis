package de.tum.cit.endpointanalysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EndpointInformation {

    @JsonProperty
    private final String requestMapping;

    @JsonProperty
    private final String endpoint;

    @JsonProperty
    private final String httpMethodAnnotation;

    @JsonProperty
    private final String URI;

    @JsonProperty
    private final String className;

    @JsonProperty
    private final int line;

    @JsonProperty
    private final List<String> otherAnnotations;

    public EndpointInformation(String requestMapping, String endpoint, String httpMethodAnnotation, String URI, String className, int line, List<String> otherAnnotations) {
        this.requestMapping = requestMapping;
        this.endpoint = endpoint;
        this.httpMethodAnnotation = httpMethodAnnotation;
        this.URI = URI;
        this.className = className;
        this.line = line;
        this.otherAnnotations = otherAnnotations;
    }

    public List<String> getOtherAnnotations() {
        return otherAnnotations;
    }

    public String getRequestMapping() {
        return requestMapping;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getHttpMethodAnnotation() {
        return httpMethodAnnotation;
    }

    public String getURI() {
        return URI;
    }

    public String getClassName() {
        return className;
    }

    public int getLine() {
        return line;
    }

    public String buildCompleteEndpointURI() {
        StringBuilder result = new StringBuilder();
        if (this.requestMapping != null && !this.requestMapping.isEmpty()) {
            result.append(this.requestMapping.replace("\"", ""));
        }
        result.append(this.URI.replace("\"", ""));
        return result.toString();
    }

    String buildComparableEndpointUri() {
        // Replace arguments with placeholder
        return this.buildCompleteEndpointURI().replaceAll("\\{.*?\\}", ":param:");
    }

    @JsonIgnore
    public String getHttpMethod() {
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
