package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("gitlab")
public class GitlabSshCloneLinkInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.version-control.ssh-template-clone-url:}")
    private String GITLAB_SSH_URL_TEMPLATE;

    @Value("${artemis.version-control.ssh-keys-path:}")
    private String GITLAB_SSH_KEYS_PATH;

    @Override
    public void contribute(Info.Builder builder) {

        final var sshKeysUrl = GITLAB_SERVER_URL + GITLAB_SSH_KEYS_PATH;

        builder.withDetail(Constants.INFO_SSH_CLONE_URL_DETAIL, GITLAB_SSH_URL_TEMPLATE);
        builder.withDetail(Constants.INFO_SSH_KEYS_URL_DETAIL, sshKeysUrl);
    }
}
