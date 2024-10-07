package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_THEIA;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_THEIA)
@Component
public class TheiaInfoContributor implements InfoContributor {

    @Value("${theia.portal-url}")
    private URL theiaPortalURL;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.THEIA_PORTAL_URL, theiaPortalURL);
    }
}
