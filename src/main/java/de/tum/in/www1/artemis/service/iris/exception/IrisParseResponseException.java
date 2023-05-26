package de.tum.in.www1.artemis.service.iris.exception;

public class IrisParseResponseException extends IrisException {

    public IrisParseResponseException(Throwable cause) {
        super("Unable to parse response of model", cause, "artemisApp.iris.error.parseResponse");
    }
}
