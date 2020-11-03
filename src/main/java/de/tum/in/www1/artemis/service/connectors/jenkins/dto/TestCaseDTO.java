package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.List;

public class TestCaseDTO {

    private String name;

    private String classname;

    private double time;

    private List<ErrorOrFailureDTO> failures;

    private List<ErrorOrFailureDTO> errors;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClassname() {
        return classname;
    }

    public void setClassname(String classname) {
        this.classname = classname;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public List<ErrorOrFailureDTO> getFailures() {
        return failures;
    }

    public void setFailures(List<ErrorOrFailureDTO> failures) {
        this.failures = failures;
    }

    public List<ErrorOrFailureDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorOrFailureDTO> errors) {
        this.errors = errors;
    }
}
