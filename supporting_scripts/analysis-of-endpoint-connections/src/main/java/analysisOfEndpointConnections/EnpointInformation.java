package analysisOfEndpointConnections;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import java.util.List;

public class EnpointInformation {
    @JsonProperty
    private String requestMapping;

    @JsonProperty
    private String endpoint;

    @JsonProperty
    private String httpMethodAnnotation;

    @JsonProperty
    private String path;

    @JsonProperty
    private String className;

    @JsonProperty
    private int line;

    @JsonProperty
    private List<String> otherAnnotations;

    public EnpointInformation(String requestMapping, String endpoint, String httpMethodAnnotation, String path, String className, int line, List<String> otherAnnotations) {
        this.requestMapping = requestMapping;
        this.endpoint = endpoint;
        this.httpMethodAnnotation = httpMethodAnnotation;
        this.path = path;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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
}
