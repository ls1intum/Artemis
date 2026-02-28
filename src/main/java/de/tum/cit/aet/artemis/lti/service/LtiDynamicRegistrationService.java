package de.tum.cit.aet.artemis.lti.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.dto.Lti13ClientRegistration;
import de.tum.cit.aet.artemis.lti.dto.Lti13ClientRegistrationFactory;
import de.tum.cit.aet.artemis.lti.dto.Lti13PlatformConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;

@Lazy
@Service
@Conditional(LtiEnabled.class)
public class LtiDynamicRegistrationService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private static final Logger log = LoggerFactory.getLogger(LtiDynamicRegistrationService.class);

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private final OAuth2JWKSService oAuth2JWKSService;

    private final RestTemplate restTemplate;

    public LtiDynamicRegistrationService(LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository, OAuth2JWKSService oAuth2JWKSService, RestTemplate restTemplate) {
        this.oAuth2JWKSService = oAuth2JWKSService;
        this.restTemplate = restTemplate;
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    /**
     * Performs dynamic registration.
     *
     * @param openIdConfigurationUrl the url to get the configuration from
     * @param registrationToken      the token to be used to authenticate the POST request
     */
    public void performDynamicRegistration(String openIdConfigurationUrl, String registrationToken) {
        try {
            // Get platform's configuration
            Lti13PlatformConfiguration platformConfiguration = getLti13PlatformConfiguration(openIdConfigurationUrl);

            String clientRegistrationId = "artemis-" + UUID.randomUUID();

            if (platformConfiguration.authorizationEndpoint() == null || platformConfiguration.tokenEndpoint() == null || platformConfiguration.jwksUri() == null
                    || platformConfiguration.registrationEndpoint() == null) {
                throw new BadRequestAlertException("Invalid platform configuration", "LTI", "invalidPlatformConfiguration");
            }

            // Validate all URLs from the platform configuration to prevent delayed SSRF
            validateExternalUrl(platformConfiguration.authorizationEndpoint());
            validateExternalUrl(platformConfiguration.tokenEndpoint());
            validateExternalUrl(platformConfiguration.jwksUri());
            validateExternalUrl(platformConfiguration.registrationEndpoint());

            var clientRegistrationResponse = postClientRegistrationToPlatform(platformConfiguration.registrationEndpoint(), clientRegistrationId, registrationToken);

            LtiPlatformConfiguration ltiPlatformConfiguration = updateLtiPlatformConfiguration(clientRegistrationId, platformConfiguration, clientRegistrationResponse);
            ltiPlatformConfigurationRepository.save(ltiPlatformConfiguration);

            oAuth2JWKSService.updateKey(clientRegistrationId);
        }
        catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("HTTP error during dynamic registration", ex);
            throw new IllegalStateException("Error during LTI dynamic registration: " + ex.getMessage(), ex);
        }
    }

    /**
     * Validates that the given URL uses HTTPS and does not point to a private, internal, or reserved network address.
     * This prevents SSRF attacks where an attacker could make the server request internal resources.
     * <p>
     *
     * @param url the URL to validate
     */
    private void validateExternalUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("Invalid URL format", "LTI", "invalidUrl");
        }

        if (!"https".equals(uri.getScheme())) {
            throw new BadRequestAlertException("Only HTTPS URLs are allowed for LTI configuration", "LTI", "invalidUrl");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new BadRequestAlertException("URL must contain a valid host", "LTI", "invalidUrl");
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        }
        catch (UnknownHostException e) {
            throw new BadRequestAlertException("URL host could not be resolved", "LTI", "invalidUrl");
        }

        if (isPrivateOrReservedAddress(address)) {
            throw new BadRequestAlertException("URLs pointing to internal or loopback addresses are not allowed", "LTI", "invalidUrl");
        }
    }

    /**
     * Checks whether the given address belongs to a private, loopback, link-local, or other reserved range
     * that should not be targeted by outbound HTTP requests.
     *
     * @param address the resolved address to check
     * @return true if the address is private or reserved
     */
    private boolean isPrivateOrReservedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }

        byte[] addr = address.getAddress();

        if (addr.length == 4) {
            int firstOctet = addr[0] & 0xFF;
            int secondOctet = addr[1] & 0xFF;
            // 100.64.0.0/10 - Shared Address Space (CGN, used by some cloud providers)
            if (firstOctet == 100 && (secondOctet & 0xC0) == 64) {
                return true;
            }
            // 0.0.0.0/8 - "This" network
            if (firstOctet == 0) {
                return true;
            }
        }

        if (addr.length == 16) {
            // IPv4-mapped IPv6 (::ffff:x.x.x.x) - extract the IPv4 portion and re-check
            boolean isV4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (addr[i] != 0) {
                    isV4Mapped = false;
                    break;
                }
            }
            if (isV4Mapped && (addr[10] & 0xFF) == 0xFF && (addr[11] & 0xFF) == 0xFF) {
                byte[] v4Addr = new byte[] { addr[12], addr[13], addr[14], addr[15] };
                try {
                    return isPrivateOrReservedAddress(InetAddress.getByAddress(v4Addr));
                }
                catch (UnknownHostException e) {
                    return true;
                }
            }
            // IPv4-compatible IPv6 (::x.x.x.x) - deprecated (RFC 4291) but still parsed by Java
            if (isV4Mapped) {
                // bytes 0-9 are zero but bytes 10-11 are not 0xFF, so this is an IPv4-compatible address
                byte[] v4Addr = new byte[] { addr[12], addr[13], addr[14], addr[15] };
                try {
                    return isPrivateOrReservedAddress(InetAddress.getByAddress(v4Addr));
                }
                catch (UnknownHostException e) {
                    return true;
                }
            }
            // fc00::/7 - IPv6 Unique Local Addresses
            if ((addr[0] & 0xFE) == 0xFC) {
                return true;
            }
        }

        return false;
    }

    private Lti13PlatformConfiguration getLti13PlatformConfiguration(String openIdConfigurationUrl) {
        validateExternalUrl(openIdConfigurationUrl);

        Lti13PlatformConfiguration platformConfiguration = null;
        try {
            ResponseEntity<Lti13PlatformConfiguration> responseEntity = restTemplate.getForEntity(openIdConfigurationUrl, Lti13PlatformConfiguration.class);
            log.info("Got LTI13 configuration from {}", openIdConfigurationUrl);
            platformConfiguration = responseEntity.getBody();
        }
        catch (HttpClientErrorException e) {
            log.error("Could not get configuration from {}", openIdConfigurationUrl);
        }

        if (platformConfiguration == null) {
            throw new BadRequestAlertException("Could not get configuration from external LMS", "LTI", "getConfigurationFailed");
        }
        return platformConfiguration;
    }

    private Lti13ClientRegistration postClientRegistrationToPlatform(String registrationEndpoint, String clientRegistrationId, String registrationToken) {
        validateExternalUrl(registrationEndpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (registrationToken != null) {
            headers.setBearerAuth(registrationToken);
        }

        Lti13ClientRegistration lti13ClientRegistration = Lti13ClientRegistrationFactory.createRegistration(artemisServerUrl, clientRegistrationId);
        Lti13ClientRegistration registrationResponse = null;
        try {
            ResponseEntity<Lti13ClientRegistration> response = restTemplate.postForEntity(registrationEndpoint, new HttpEntity<>(lti13ClientRegistration, headers),
                    Lti13ClientRegistration.class);
            log.info("Registered {} as LTI1.3 tool at {}", artemisServerUrl, registrationEndpoint);
            registrationResponse = response.getBody();
        }
        catch (HttpClientErrorException e) {
            String message = "Could not register new client in external LMS at " + registrationEndpoint;
            log.error(message);
        }

        if (registrationResponse == null) {
            throw new BadRequestAlertException("Could not register configuration in external LMS", "LTI", "postConfigurationFailed");
        }
        return registrationResponse;
    }

    private LtiPlatformConfiguration updateLtiPlatformConfiguration(String registrationId, Lti13PlatformConfiguration platformConfiguration,
            Lti13ClientRegistration clientRegistrationResponse) {
        LtiPlatformConfiguration ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setRegistrationId(registrationId);
        ltiPlatformConfiguration.setClientId(clientRegistrationResponse.clientId());
        ltiPlatformConfiguration.setAuthorizationUri(platformConfiguration.authorizationEndpoint());
        ltiPlatformConfiguration.setJwkSetUri(platformConfiguration.jwksUri());
        ltiPlatformConfiguration.setTokenUri(platformConfiguration.tokenEndpoint());
        ltiPlatformConfiguration.setOriginalUrl(platformConfiguration.issuer());
        return ltiPlatformConfiguration;
    }
}
