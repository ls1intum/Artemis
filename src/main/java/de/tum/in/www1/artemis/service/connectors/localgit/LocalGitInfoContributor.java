package de.tum.in.www1.artemis.service.connectors.localgit;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("localgit")
public class LocalGitInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @Value("${artemis.version-control.ssh-template-clone-url:#{null}}")
    private Optional<String> bitbucketSshUrlTemplate;

    @Value("${artemis.version-control.ssh-keys-url-path:#{null}}")
    private Optional<String> bitbucketSshKeysUrlPath;

    @Override
    public void contribute(Info.Builder builder) {
        // Store server url
        builder.withDetail(Constants.VERSION_CONTROL_URL, bitbucketServerUrl);

        // Store commit hash url template
        String commitHashPathTemplate = "/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}";
        String commitHashUrlTemplate = bitbucketServerUrl + commitHashPathTemplate;
        builder.withDetail(Constants.INFO_COMMIT_HASH_URL_DETAIL, commitHashUrlTemplate);

        // Store ssh url template
        if (bitbucketSshUrlTemplate.isPresent()) {
            builder.withDetail(Constants.INFO_SSH_CLONE_URL_DETAIL, bitbucketSshUrlTemplate);
            if (bitbucketSshKeysUrlPath.isPresent()) {
                final var sshKeysUrl = bitbucketServerUrl + bitbucketSshKeysUrlPath.get();
                builder.withDetail(Constants.INFO_SSH_KEYS_URL_DETAIL, sshKeysUrl);
            }
        }

    }
}
