package de.tum.in.www1.artemis.service.connectors.lti;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
public class ExternalPasswordResetInfoContributor implements InfoContributor {

    @Value("${artemis.user-management.password-reset.credential-provider}")
    private String credentialProvider;

    @Value("#{${artemis.user-management.password-reset.link}}")
    private Map<String, String> linkMap;

    @Value("${artemis.user-management.use-external")
    private boolean useExternal;

    /**
     * Contributes additional details using the specified {@link Info.Builder Builder}.
     *
     * @param builder the builder to use
     */
    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.EXTERNAL_CREDENTIAL_PROVIDER, credentialProvider);
        builder.withDetail(Constants.EXTERNAL_PASSWORD_RESET_LINK_MAP, linkMap);
        builder.withDetail(Constants.USE_EXTERNAL, useExternal);
    }
}
