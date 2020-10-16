package de.tum.in.www1.artemis.service.connectors.bitbucket;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("bitbucket")
public class BitbucketSshCloneLinkInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @Value("${artemis.version-control.ssh-template-clone-url:#{null}}")
    private Optional<String> bitbucketSshUrlTemplate;

    @Value("${artemis.version-control.ssh-keys-url-path:#{null}}")
    private Optional<String> bitbucketSshKeysUrlPath;

    @Override
    public void contribute(Info.Builder builder) {

        builder.withDetail(Constants.VERSION_CONTROL_URL, bitbucketServerUrl);

        if (bitbucketSshUrlTemplate.isPresent()) {
            builder.withDetail(Constants.INFO_SSH_CLONE_URL_DETAIL, bitbucketSshUrlTemplate);
            if (bitbucketSshKeysUrlPath.isPresent()) {
                final var sshKeysUrl = bitbucketServerUrl + bitbucketSshKeysUrlPath.get();
                builder.withDetail(Constants.INFO_SSH_KEYS_URL_DETAIL, sshKeysUrl);
            }
        }

    }
}
