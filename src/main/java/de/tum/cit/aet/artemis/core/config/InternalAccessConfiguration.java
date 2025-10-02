package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

@Profile(PROFILE_CORE)
@Lazy
@ConfigurationProperties(prefix = "artemis.security.internal")
public class InternalAccessConfiguration {

    /**
     * List of CIDR blocks (IPv4/IPv6) allowed to access @Internal endpoints.
     */
    private List<String> allowedCidrs;

    public List<String> getAllowedCidrs() {
        return allowedCidrs;
    }

    public void setAllowedCidrs(List<String> allowedCidrs) {
        this.allowedCidrs = allowedCidrs;
    }
}
