package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * REST controller for the apple-app-site-association json
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.MOBILE_APPS)
@RestController
@RequestMapping(".well-known/") // Intentionally not prefixed with "communication"
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
    @UsageInteraction(FeatureInteraction.SYSTEM)
    @GetMapping("apple-app-site-association")
    @ManualConfig
    public ResponseEntity<AppleAppSiteAssociation> getAppleAppSiteAssociation() {
        if (appId == null || appId.length() < 10) {
            log.debug("Apple AppID is not configured!");
            return ResponseEntity.notFound().build();
        }

        List<String> paths = List.of("/courses/*");
        AppleAppSiteAssociation.Applinks.Detail detail = new AppleAppSiteAssociation.Applinks.Detail(appId, paths);
        List<AppleAppSiteAssociation.Applinks.Detail> details = List.of(detail);
        List<String> apps = List.of();
        AppleAppSiteAssociation.Applinks applinks = new AppleAppSiteAssociation.Applinks(apps, details);

        List<String> webcredentialApps = List.of(appId);
        AppleAppSiteAssociation.Webcredentials webcredentials = new AppleAppSiteAssociation.Webcredentials(webcredentialApps);

        AppleAppSiteAssociation appleAppSiteAssociation = new AppleAppSiteAssociation(applinks, webcredentials);

        return ResponseEntity.ok(appleAppSiteAssociation);
    }

    public record AppleAppSiteAssociation(Applinks applinks, Webcredentials webcredentials) {

        public record Webcredentials(List<String> apps) {
        }

        public record Applinks(List<String> apps, List<Detail> details) {

            public record Detail(String appID, List<String> paths) {
            }
        }
    }

}
