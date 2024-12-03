package de.tum.cit.aet.artemis.core.exception;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by Spring profiles not being present.
 */
public class ApiNotPresentException extends RuntimeException {

    /**
     * Constructor.
     *
     * @param api     name of the api class
     * @param profile name of the Spring profile that needs to be enabled.
     */
    public ApiNotPresentException(String api, String profile) {
        super(String.format("Api %s is not enabled, because Spring profile %s is not enabled. Did you enable it?", api, profile));
    }
}
