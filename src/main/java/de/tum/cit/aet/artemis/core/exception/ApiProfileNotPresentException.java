package de.tum.cit.aet.artemis.core.exception;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by Spring profiles not being present.
 */
public class ApiProfileNotPresentException extends RuntimeException {

    /**
     * @param api     the api class that should be present
     * @param profile name of the Spring profile that needs to be enabled.
     */
    public ApiProfileNotPresentException(Class<? extends AbstractApi> api, String profile) {
        super(String.format("Api %s is not enabled, because Spring profile %s is not enabled. Did you enable it?", api.getName(), profile));
    }
}
