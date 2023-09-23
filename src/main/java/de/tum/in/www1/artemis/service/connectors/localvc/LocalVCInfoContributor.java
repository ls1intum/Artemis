package de.tum.in.www1.artemis.service.connectors.localvc;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("localvc")
public class LocalVCInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Override
    public void contribute(Info.Builder builder) {
        // Store server url
        builder.withDetail(Constants.VERSION_CONTROL_URL, localVCBaseUrl);

        // Store name of the version control system
        builder.withDetail(Constants.VERSION_CONTROL_NAME, "Local VC");
    }
}
