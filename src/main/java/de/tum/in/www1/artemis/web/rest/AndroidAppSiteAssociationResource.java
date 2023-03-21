package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/.well-known")
public class AndroidAppSiteAssociationResource {

    @Value("${artemis.androidAppPackage}")
    private String androidAppPackage;

    @Value("${artemis.androidSha256CertFingerprints}")
    private String sha256CertFingerprints;

    @GetMapping("/assetlinks.json")
    public ResponseEntity<List<AndroidAssetLinksEntry>> getAndroidAssetLinks() {
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
