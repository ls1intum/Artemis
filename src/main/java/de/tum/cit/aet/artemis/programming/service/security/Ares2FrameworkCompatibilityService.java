package de.tum.cit.aet.artemis.programming.service.security;

import java.util.List;

/**
 * Boundary to Ares2's framework-compatibility checks. A real implementation would ask Ares2 which
 * framework releases it can generate and enforce a policy for; {@link MockAres2FrameworkCompatibilityService}
 * stands in until that integration exists.
 */
public interface Ares2FrameworkCompatibilityService {

    /**
     * @return the framework versions Ares2 can generate a policy for, newest first.
     */
    List<String> getSupportedFrameworkVersions();

    /**
     * @param frameworkVersion the version to check
     * @return whether Ares2 supports generating and enforcing a policy with this framework version
     */
    boolean isFrameworkVersionSupported(String frameworkVersion);
}
