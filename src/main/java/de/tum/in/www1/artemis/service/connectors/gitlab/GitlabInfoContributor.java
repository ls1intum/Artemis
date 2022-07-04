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
public class GitlabInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${artemis.version-control.ssh-template-clone-url:#{null}}")
    private Optional<String> gitlabSshUrlTemplate;

    @Value("${artemis.version-control.ssh-keys-url-path:#{null}}")
    private Optional<String> gitlabSshKeysUrlPath;

    @Value("${artemis.version-control.version-control-access-token:#{false}}")
    private Boolean versionControlAccessToken;

    @Override
    public void contribute(Info.Builder builder) {
        // Store server url
        builder.withDetail(Constants.VERSION_CONTROL_URL, gitlabServerUrl);

        // Store commit hash url template
        String commitHashPathTemplate = "/{projectKey}/{repoSlug}/-/commit/{commitHash}";
        String commitHashUrlTemplate = gitlabServerUrl + commitHashPathTemplate;
        builder.withDetail(Constants.INFO_COMMIT_HASH_URL_DETAIL, commitHashUrlTemplate);

        // Store ssh url template
        if (gitlabSshUrlTemplate.isPresent()) {
            builder.withDetail(Constants.INFO_SSH_CLONE_URL_DETAIL, gitlabSshUrlTemplate);
            if (gitlabSshKeysUrlPath.isPresent()) {
                final var sshKeysUrl = gitlabServerUrl + gitlabSshKeysUrlPath.get();
                builder.withDetail(Constants.INFO_SSH_KEYS_URL_DETAIL, sshKeysUrl);
            }
        }

        builder.withDetail(Constants.INFO_VERSION_CONTROL_ACCESS_TOKEN_DETAIL, versionControlAccessToken);
    }
}
