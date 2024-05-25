package de.tum.in.www1.artemis.service.connectors.localvc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("localvc")
public class LocalVCInfoContributor implements InfoContributor {

    private static final Logger log = LoggerFactory.getLogger(LocalVCInfoContributor.class);

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.version-control.ssh-port:7921}")
    private int sshPort;

    @Override
    public void contribute(Info.Builder builder) {
        // Store server url
        builder.withDetail(Constants.VERSION_CONTROL_URL, localVCBaseUrl);

        // Store name of the version control system
        builder.withDetail(Constants.VERSION_CONTROL_NAME, "Local VC");

        // Show the access token in case it is available in the clone URL
        builder.withDetail(Constants.INFO_VERSION_CONTROL_ACCESS_TOKEN_DETAIL, true);

        // Store ssh url template
        try {
            var serverUri = new URI(artemisServerUrl);
            builder.withDetail(Constants.INFO_SSH_CLONE_URL_DETAIL, "ssh://git@" + serverUri.getHost() + ":" + sshPort + "/");
        }
        catch (URISyntaxException e) {
            log.error("Failed to parse server URL", e);
        }
    }
}
