package de.tum.in.www1.artemis.service.connectors.lti;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@EnableConfigurationProperties
@ConfigurationProperties("artemis.user-management.password-reset")
public class ExternalPasswordResetInfoContributor implements InfoContributor {

    private String credentialProvider;

    private Map<String, String> links;

    @Value("${artemis.user-management.use-external}")
    private String useExternal;

    public void setCredentialProvider(String credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }

    /**
     * Contributes additional details using the specified {@link Info.Builder Builder}.
     *
     * @param builder the builder to use
     */
    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.EXTERNAL_CREDENTIAL_PROVIDER, credentialProvider);
        builder.withDetail(Constants.EXTERNAL_PASSWORD_RESET_LINK_MAP, links);
        builder.withDetail(Constants.USE_EXTERNAL, useExternal.equals("true"));
    }
}
