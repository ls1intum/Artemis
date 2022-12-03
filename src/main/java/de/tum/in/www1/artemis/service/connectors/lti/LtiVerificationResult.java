package de.tum.in.www1.artemis.service.connectors.lti;

public class LtiVerificationResult {

    private Boolean success;

    private LtiError error;

    private String message;

    private LtiLaunch ltiLaunchResult;

    public LtiVerificationResult(Boolean success, LtiError error, String message) {
        this.success = success;
        this.error = error;
        this.message = message;
    }

    public LtiVerificationResult(Boolean success, LtiLaunch ltiLaunchResult) {
        this.ltiLaunchResult = ltiLaunchResult;
        this.success = success;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public LtiError getError() {
        return error;
    }

    public void setError(LtiError error) {
        this.error = error;
    }

    public LtiLaunch getLtiLaunchResult() {
        return ltiLaunchResult;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
