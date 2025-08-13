package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.INFO_CODE_BUTTON_REPOSITORY_AUTHENTICATION_MECHANISMS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Component
@Lazy
@Profile(PROFILE_LOCALVC)
public class LocalVCInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${server.url}")
    private URI artemisServerUri;

    @Value("${artemis.version-control.repository-authentication-mechanisms:password,token,ssh}")
    private List<String> orderedRepositoryAuthenticationMechanisms;

    @Value("${artemis.version-control.ssh-port:7921}")
    private int sshPort;

    @Override
    public void contribute(Info.Builder builder) {
        // Store server url
        builder.withDetail(Constants.VERSION_CONTROL_URL, localVCBaseUri);

        // Store name of the version control system
        builder.withDetail(Constants.VERSION_CONTROL_NAME, "Local VC");

        // Store the authentication mechanisms that should be used by the code-button and their order
        builder.withDetail(INFO_CODE_BUTTON_REPOSITORY_AUTHENTICATION_MECHANISMS, orderedRepositoryAuthenticationMechanisms);
        // Store ssh url template
        builder.withDetail(Constants.INFO_SSH_CLONE_URL_DETAIL, "ssh://git@" + artemisServerUri.getHost() + ":" + sshPort + "/");
    }
}
