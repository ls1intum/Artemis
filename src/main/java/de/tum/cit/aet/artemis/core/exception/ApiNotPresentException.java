package de.tum.cit.aet.artemis.core.exception;

public class ApiNotPresentException extends RuntimeException {

    public ApiNotPresentException(String api, String profile) {
        super(String.format("Api %s is not enabled, because Spring profile %s is not enabled. Did you enable it?", api, profile));
    }
}
