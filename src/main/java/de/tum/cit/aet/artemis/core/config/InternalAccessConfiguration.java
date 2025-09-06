package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.internal")
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
