package de.tum.cit.aet.artemis.core.exception;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by a Spring property not being present.
 */
public class ApiConditionNotPresentException extends RuntimeException {

    /**
     * @param api          the api class that should be present
     * @param propertyName name of the Spring property that needs to be enabled.
     */
    public ApiConditionNotPresentException(Class<? extends AbstractApi> api, String propertyName) {
        super(String.format("Api %s is not enabled, because property %s is not enabled. Did you override it in your application.yml?", api.getName(), propertyName));
    }
}
