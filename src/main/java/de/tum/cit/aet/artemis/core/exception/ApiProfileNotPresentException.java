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
        super(String.format(
                "Api %s is not enabled because '%s' is not active. Enable the module feature via artemis.%s.enabled=true, or activate the corresponding legacy Spring profile where applicable.",
                api.getName(), featureOrProfile, featureOrProfile));
    }
}
