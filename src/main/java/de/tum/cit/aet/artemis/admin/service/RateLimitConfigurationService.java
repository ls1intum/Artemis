package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * Service for managing rate limiting configuration.
 * Provides centralized access to rate limiting settings including enable/disable flags
 * and configurable RPM values for different endpoint types.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class RateLimitConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfigurationService.class);

    private final RateLimitingProperties properties;

    /**
     * Exempt addresses parsed once at startup, so the hot path is a list walk rather than a parse.
     * Held as {@link IPAddress} so a CIDR block matches every address inside it, and so IPv4 and IPv6
     * are compared as addresses rather than as strings: the same host can present itself as
     * {@code ::ffff:127.0.0.1} or {@code 127.0.0.1} depending on the connector.
     */
    private final List<IPAddress> exemptAddresses;

    public RateLimitConfigurationService(RateLimitingProperties properties) {
        this.properties = properties;
        this.exemptAddresses = parseExemptAddresses(properties.getExemptAddresses());
    }

    private static List<IPAddress> parseExemptAddresses(List<String> configured) {
        List<IPAddress> parsed = new ArrayList<>();
        for (String entry : configured) {
            String trimmed = entry == null ? "" : entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                // Rejecting an unparseable entry at startup would take the whole application down over a
                // typo in an optional convenience setting, so log it and carry on with the rest.
                parsed.add(normalise(new IPAddressString(trimmed).toAddress()));
            }
            catch (AddressStringException e) {
                log.error("Ignoring unparseable rate limit exempt address '{}': {}", trimmed, e.getMessage());
            }
        }
        if (!parsed.isEmpty()) {
            log.info("Rate limiting is exempt for {} configured address(es) or range(s)", parsed.size());
        }
        return List.copyOf(parsed);
    }

    /**
     * Collapses an IPv4-mapped IPv6 address such as {@code ::ffff:203.0.113.10} to its IPv4 form.
     *
     * {@link IPAddress#contains} only matches inside one address version, so both the configured entries
     * and the client address have to be reduced to the same representation before they are compared.
     * Applying this to the configured side as well is what makes the match symmetric: an entry written in
     * either form then exempts a client arriving in either form.
     *
     * @param address the address to normalise
     * @return the IPv4 form when the address is IPv4-convertible, otherwise the address unchanged
     */
    private static IPAddress normalise(IPAddress address) {
        return address.isIPv4Convertible() ? address.toIPv4() : address;
    }

    /**
     * Whether the given client is exempt from every rate limit.
     *
     * @param clientId the client address, usually taken from the request
     * @return true if the address is listed as exempt, or falls inside a listed range
     */
    public boolean isExempt(IPAddress clientId) {
        if (clientId == null || exemptAddresses.isEmpty()) {
            return false;
        }
        IPAddress candidate = normalise(clientId);
        return exemptAddresses.stream().anyMatch(exempt -> exempt.contains(candidate) || exempt.contains(clientId));
    }

    /**
     * Checks if rate limiting is enabled globally.
     *
     * @return true if rate limiting is enabled, false otherwise (default: false)
     */
    public boolean isRateLimitingEnabled() {
        return properties.isEnabled();
    }

    /**
     * Gets the effective RPM value for a given rate limit type.
     * Returns the configured value if available, otherwise falls back to the default.
     *
     * @param type the rate limit type
     * @return the effective RPM value
     */
    public int getEffectiveRpm(RateLimitType type) {
        return switch (type) {
            case ACCOUNT_MANAGEMENT -> properties.getAccountManagementRequestsPerMinute() != null ? properties.getAccountManagementRequestsPerMinute() : type.getDefaultRpm();
            case AUTHENTICATION -> properties.getAuthenticationRequestsPerMinute() != null ? properties.getAuthenticationRequestsPerMinute() : type.getDefaultRpm();
            case LOGIN_OPTIONS -> properties.getLoginOptionsRequestsPerMinute() != null ? properties.getLoginOptionsRequestsPerMinute() : type.getDefaultRpm();
            case PROBLEM_STATEMENT_RENDERING ->
                properties.getProblemStatementRenderingRequestsPerMinute() != null ? properties.getProblemStatementRenderingRequestsPerMinute() : type.getDefaultRpm();
            case AI_SEARCH_PIPELINE -> properties.getAiSearchPipelineRequestsPerMinute() != null ? properties.getAiSearchPipelineRequestsPerMinute() : type.getDefaultRpm();
            case BUILD_AGENT_CLONE_TOKEN ->
                properties.getBuildAgentCloneTokenRequestsPerMinute() != null ? properties.getBuildAgentCloneTokenRequestsPerMinute() : type.getDefaultRpm();
            case REPOSITORY_EDITOR -> properties.getRepositoryEditorRequestsPerMinute() != null ? properties.getRepositoryEditorRequestsPerMinute() : type.getDefaultRpm();
        };
    }
}
