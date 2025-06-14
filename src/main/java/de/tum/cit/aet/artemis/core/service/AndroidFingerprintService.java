package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Helper service for getting the fingerprints for the Android app.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class AndroidFingerprintService {

    private static final Logger log = LoggerFactory.getLogger(AndroidFingerprintService.class);

    @Value("${info.testServer:false}")
    private boolean isTestServer;

    @Value("${artemis.androidSha256CertFingerprints.release: #{null}}")
    private String androidSha256CertFingerprintRelease;

    @Value("${artemis.androidSha256CertFingerprints.debug: #{null}}")
    private String androidSha256CertFingerprintDebug;

    private final ProfileService profileService;

    public AndroidFingerprintService(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Returns the fingerprints for the Android app.
     * The release fingerprint is always included.
     * The debug fingerprint is only included if the production profile is not active or if the test server is used.
     *
     * @return a list of fingerprints
     */
    public List<String> getFingerprints() {
        List<String> fingerprints = new ArrayList<>();
        if (isFingerprintValid(androidSha256CertFingerprintRelease)) {
            fingerprints.add(androidSha256CertFingerprintRelease);
        }
        else {
            log.warn("The Android release fingerprint is not valid: {}", androidSha256CertFingerprintRelease);
        }

        boolean isDebugFingerprintAllowed = !profileService.isProductionActive() || isTestServer;
        if (isFingerprintValid(androidSha256CertFingerprintDebug) && isDebugFingerprintAllowed) {
            fingerprints.add(androidSha256CertFingerprintDebug);
            log.debug("Added the Android debug fingerprint: {}", androidSha256CertFingerprintDebug);
        }

        return fingerprints;
    }

    /**
     * Checks if the given fingerprint is valid.
     *
     * @param fingerprint the fingerprint to check
     * @return true if the fingerprint is valid, false otherwise
     */
    public static boolean isFingerprintValid(String fingerprint) {
        return fingerprint != null && fingerprint.length() > 20;
    }
}
