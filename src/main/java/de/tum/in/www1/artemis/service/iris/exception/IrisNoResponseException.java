package de.tum.in.www1.artemis.service.iris.exception;

public class IrisNoResponseException extends IrisException {

    public IrisNoResponseException() {
        super("No response from Iris model", "artemisApp.iris.error.noResponse");
    }
}
