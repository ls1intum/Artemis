package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BambooTestResultDTO {

    private String className;

    private String methodName;

    private String status;

    private int duration;

    private int durationInSeconds;

    private BambooTestResultErrorsDTO errors;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getDurationInSeconds() {
        return durationInSeconds;
    }

    public void setDurationInSeconds(int durationInSeconds) {
        this.durationInSeconds = durationInSeconds;
    }

    public BambooTestResultErrorsDTO getErrors() {
        return errors;
    }

    public void setErrors(BambooTestResultErrorsDTO errors) {
        this.errors = errors;
    }

    public static final class BambooTestResultErrorsDTO {

        private int size;

        @JsonProperty("max-result")
        private int maxResult;

        @JsonProperty("error")
        private List<BambooTestErrorDTO> errorMessages;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getMaxResult() {
            return maxResult;
        }

        public void setMaxResult(int maxResult) {
            this.maxResult = maxResult;
        }

        public List<BambooTestErrorDTO> getErrorMessages() {
            return errorMessages;
        }

        public void setErrorMessages(List<BambooTestErrorDTO> errorMessages) {
            this.errorMessages = errorMessages;
        }
    }

    public static final class BambooTestErrorDTO {

        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
