package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.net.URL;
import java.util.Optional;

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

    @Value("${artemis.version-control.ssh-template-clone-url:#{null}}")
    private Optional<String> GITLAB_SSH_URL_TEMPLATE;

    @Value("${artemis.version-control.ssh-keys-url-path:#{null}}")
    private Optional<String> GITLAB_SSH_KEYS_URL_PATH;

    @Override
    public void contribute(Info.Builder builder) {

        if (GITLAB_SSH_URL_TEMPLATE.isPresent()) {
            builder.withDetail(Constants.INFO_SSH_CLONE_URL_DETAIL, GITLAB_SSH_URL_TEMPLATE);
            if (GITLAB_SSH_KEYS_URL_PATH.isPresent()) {
                final var sshKeysUrl = GITLAB_SERVER_URL + GITLAB_SSH_KEYS_URL_PATH.get();
                builder.withDetail(Constants.INFO_SSH_KEYS_URL_DETAIL, sshKeysUrl);
            }
        }

    }
}
