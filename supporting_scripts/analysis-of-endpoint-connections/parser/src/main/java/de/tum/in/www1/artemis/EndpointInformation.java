package de.tum.in.www1.artemis;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EndpointInformation {

    @JsonProperty
    private String requestMapping;

    @JsonProperty
    private String endpoint;

    @JsonProperty
    private String httpMethodAnnotation;

    @JsonProperty
    private String URI;

    @JsonProperty
    private String className;

    @JsonProperty
    private int line;

    @JsonProperty
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
        String result = "";

        if (this.requestMapping != null && !this.requestMapping.isEmpty()) {
            result = this.requestMapping.replace("\"", "");
        }
        result += this.URI.replace("\"", "");
        return result;
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
