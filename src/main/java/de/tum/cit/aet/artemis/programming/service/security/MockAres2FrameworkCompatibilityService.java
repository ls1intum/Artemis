package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mock stand-in for Ares2's compatibility checks: a fixed list of supported framework versions. Replace
 * with a real Ares2-backed implementation when that integration lands.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MockAres2FrameworkCompatibilityService implements Ares2FrameworkCompatibilityService {

    private static final List<String> SUPPORTED_FRAMEWORK_VERSIONS = List.of("3.4.1", "3.3.0", "3.2.2");

    @Override
    public List<String> getSupportedFrameworkVersions() {
        return SUPPORTED_FRAMEWORK_VERSIONS;
    }

    @Override
    public boolean isFrameworkVersionSupported(String frameworkVersion) {
        return SUPPORTED_FRAMEWORK_VERSIONS.contains(frameworkVersion);
    }
}
