package de.tum.cit.aet.artemis.core.exception;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by the underlying module feature (or, for legacy components, a Spring profile) not being enabled.
 */
public class ApiProfileNotPresentException extends RuntimeException {

    /**
     * @param api              the api class that should be present
     * @param featureOrProfile name of the module feature (e.g. {@code MODULE_FEATURE_ATHENA}) or the legacy Spring profile that needs to be enabled.
     */
    public ApiProfileNotPresentException(Class<? extends AbstractApi> api, String featureOrProfile) {
        super(String.format("Api %s is not enabled, because the '%s' module feature is not enabled. Set artemis.%s.enabled=true (or the equivalent module flag) to enable it.",
                api.getName(), featureOrProfile, featureOrProfile));
    }
}
