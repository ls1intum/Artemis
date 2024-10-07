package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;

/**
 * REST controller for the apple-app-site-association json
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping(".well-known/")
public class AppleAppSiteAssociationResource {

    @Value("${artemis.iosAppId: #{null}}")
    private String appId;

    private static final Logger log = LoggerFactory.getLogger(AppleAppSiteAssociationResource.class);

    /**
     * Provides the apple-app-site-association json content for the iOS client universal link feature.
     * More information on the json content can be found <a href="https://developer.apple.com/documentation/xcode/supporting-associated-domains">here</a>
     *
     * @return apple-app-site-association as json
     */
    @GetMapping("apple-app-site-association")
    @ManualConfig
    public ResponseEntity<AppleAppSiteAssociation> getAppleAppSiteAssociation() {
        if (appId == null || appId.length() < 10) {
            log.debug("Apple AppID is not configured!");
            return ResponseEntity.notFound().build();
        }

        String[] paths = { "/courses/*" };
        AppleAppSiteAssociation.Applinks.Detail detail = new AppleAppSiteAssociation.Applinks.Detail(appId, paths);
        AppleAppSiteAssociation.Applinks.Detail[] details = { detail };
        String[] apps = {};
        AppleAppSiteAssociation.Applinks applinks = new AppleAppSiteAssociation.Applinks(apps, details);

        String[] webcredentialApps = { appId };
        AppleAppSiteAssociation.Webcredentials webcredentials = new AppleAppSiteAssociation.Webcredentials(webcredentialApps);

        AppleAppSiteAssociation appleAppSiteAssociation = new AppleAppSiteAssociation(applinks, webcredentials);

        return ResponseEntity.ok(appleAppSiteAssociation);
    }

    public record AppleAppSiteAssociation(Applinks applinks, Webcredentials webcredentials) {

        public record Webcredentials(String[] apps) {
        }

        public record Applinks(String[] apps, Detail[] details) {

            public record Detail(String appID, String[] paths) {
            }
        }
    }

}
