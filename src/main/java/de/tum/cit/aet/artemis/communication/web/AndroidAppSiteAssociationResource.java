package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.ProfileService;

/**
 * REST controller for the android assetlink.json
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping(".well-known/") // Intentionally not prefixed with "communication"
public class AndroidAppSiteAssociationResource {

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.androidAppPackage: #{null}}")
    private String androidAppPackage;

    @Value("${artemis.androidSha256CertFingerprints.release: #{null}}")
    private String sha256CertFingerprintRelease;

    @Value("${artemis.androidSha256CertFingerprints.debug: #{null}}")
    private String sha256CertFingerprintDebug;

    private static final Logger log = LoggerFactory.getLogger(AndroidAppSiteAssociationResource.class);

    private final ProfileService profileService;

    public AndroidAppSiteAssociationResource(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Provides the assetlinks json content for the Android client deeplink link feature.
     * More information on the json content can be found <a href="https://developer.android.com/training/app-links/verify-android-applinks">here</a>
     *
     * @return assetslinks as json
     */
    @GetMapping(value = "assetlinks.json", produces = "application/json")
    @ManualConfig
    public ResponseEntity<List<AndroidAssetLinksStatement>> getAndroidAssetLinks() {
        if (androidAppPackage == null || androidAppPackage.length() < 4 || sha256CertFingerprintRelease == null || sha256CertFingerprintRelease.length() < 20
        // The debug fingerprint is optional, so we don't check it
        ) {
            log.debug("Android Assetlinks information is not configured!");
            return ResponseEntity.notFound().build();
        }

        List<String> fingerprints = new ArrayList<>(List.of(sha256CertFingerprintRelease));
        if (sha256CertFingerprintDebug != null && !profileService.isProductionActive()) {
            fingerprints.add(sha256CertFingerprintDebug);
        }
        final AndroidAssetLinksStatement.AndroidTarget appTarget = new AndroidAssetLinksStatement.AndroidTarget("android_app", androidAppPackage, fingerprints);

        final AndroidAssetLinksStatement.WebTarget webTarget = new AndroidAssetLinksStatement.WebTarget("web", artemisServerUrl);

        final List<String> relations = List.of("delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds");

        final AndroidAssetLinksStatement appStatement = new AndroidAssetLinksStatement(relations, appTarget);
        final AndroidAssetLinksStatement webStatement = new AndroidAssetLinksStatement(relations, webTarget);

        return ResponseEntity.ok(List.of(appStatement, webStatement));
    }

    public record AndroidAssetLinksStatement(List<String> relation, Target target) {

        public interface Target {
        }

        public record AndroidTarget(String namespace, String package_name, List<String> sha256_cert_fingerprints) implements Target {
        }

        public record WebTarget(String namespace, String site) implements Target {
        }
    }
}
