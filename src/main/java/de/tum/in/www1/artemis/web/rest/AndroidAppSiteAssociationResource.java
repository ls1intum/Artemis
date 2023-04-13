package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.ManualConfig;

/**
 * REST controller for the android assetlink.json
 */
@RestController
@RequestMapping("/.well-known")
public class AndroidAppSiteAssociationResource {

    @Value("${artemis.androidAppPackage: #{null}}")
    private String androidAppPackage;

    @Value("${artemis.androidSha256CertFingerprints: #{null}}")
    private String sha256CertFingerprints;

    private final Logger log = LoggerFactory.getLogger(AndroidAppSiteAssociationResource.class);

    /**
     * Provides the assetlinks json content for the Android client deeplink link feature.
     * More information on the json content can be found <a href="URL#https://developer.android.com/training/app-links/verify-android-applinks">here</a>
     *
     * @return assetslinks as json
     */
    @GetMapping("/assetlinks.json")
    @ManualConfig
    public ResponseEntity<List<AndroidAssetLinksEntry>> getAndroidAssetLinks() {
        if (androidAppPackage == null || androidAppPackage.length() < 4 || sha256CertFingerprints == null || sha256CertFingerprints.length() < 20) {
            log.debug("Android Assetlinks information is not configured!");
            return ResponseEntity.notFound().build();
        }

        final AndroidAssetLinksEntry.AndroidTarget appTarget = new AndroidAssetLinksEntry.AndroidTarget("android_app", androidAppPackage, List.of(sha256CertFingerprints));

        final AndroidAssetLinksEntry handleAllUrls = new AndroidAssetLinksEntry(List.of("delegate_permission/common.handle_all_urls"), appTarget);

        final AndroidAssetLinksEntry getLoginCredentials = new AndroidAssetLinksEntry(List.of("delegate_permission/common.get_login_creds"), appTarget);

        return ResponseEntity.ok(List.of(handleAllUrls, getLoginCredentials));
    }

    record AndroidAssetLinksEntry(List<String> relation, AndroidTarget target) {

        record AndroidTarget(String namespace, String package_name, List<String> sha256_cert_fingerprints) {
        }
    }
}
