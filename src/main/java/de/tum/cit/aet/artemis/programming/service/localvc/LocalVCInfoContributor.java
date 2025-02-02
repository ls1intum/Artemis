package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.INFO_CODE_BUTTON_REPOSITORY_AUTHENTICATION_MECHANISMS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Component
@Profile(PROFILE_LOCALVC)
public class LocalVCInfoContributor implements InfoContributor {

    private static final Logger log = LoggerFactory.getLogger(LocalVCInfoContributor.class);

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.version-control.repository-authentication-mechanisms:password,token,ssh}")
    private List<String> orderedRepositoryAuthenticationMechanisms;

    @Value("${artemis.version-control.ssh-port:7921}")
    private int sshPort;

    @Override
    public void contribute(Info.Builder builder) {
        // Store server url
        builder.withDetail(Constants.VERSION_CONTROL_URL, localVCBaseUrl);

        // Store name of the version control system
        builder.withDetail(Constants.VERSION_CONTROL_NAME, "Local VC");

        // Store the authentication mechanisms that should be used by the code-button and their order
        builder.withDetail(INFO_CODE_BUTTON_REPOSITORY_AUTHENTICATION_MECHANISMS, orderedRepositoryAuthenticationMechanisms);
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
