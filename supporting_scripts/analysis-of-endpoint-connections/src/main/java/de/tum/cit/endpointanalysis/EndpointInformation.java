package de.tum.cit.endpointanalysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EndpointInformation {

    private String requestMapping;

    private String endpoint;

    private String httpMethodAnnotation;

    private String URI;

    private String className;

    private int line;

    private List<String> otherAnnotations;

    public EndpointInformation() {

    }

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

    public void setOtherAnnotations(List<String> otherAnnotations) {
        this.otherAnnotations = otherAnnotations;
    }

    public String getRequestMapping() {
        return requestMapping;
    }

    public void setRequestMapping(String requestMapping) {
        this.requestMapping = requestMapping;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getHttpMethodAnnotation() {
        return httpMethodAnnotation;
    }

    public void setHttpMethodAnnotation(String httpMethodAnnotation) {
        this.httpMethodAnnotation = httpMethodAnnotation;
    }

    public String getURI() {
        return URI;
    }

    public void setURI(String URI) {
        this.URI = URI;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
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
