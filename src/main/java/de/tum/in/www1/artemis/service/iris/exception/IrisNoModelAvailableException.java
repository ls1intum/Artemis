package de.tum.in.www1.artemis.service.iris.exception;

public class IrisNoModelAvailableException extends IrisException {

    public IrisNoModelAvailableException() {
        super("No model available", "artemisApp.iris.error.noModelAvailable");
    }
}
