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

/**
 * Handles LTI 1.3 Dynamic Registration as defined in
 * <a href="https://www.imsglobal.org/spec/lti-dr/v1p0">IMS LTI Dynamic Registration 1.0</a>.
 * <p>
 * The registration flow consists of three steps:
 * <ol>
 * <li>Fetch the platform's OpenID Connect configuration from the provided URL.</li>
 * <li>POST Artemis' tool registration to the platform's registration endpoint.</li>
 * <li>Persist the resulting {@link LtiPlatformConfiguration} and generate a JWKS key pair.</li>
 * </ol>
 * <p>
 * Every URL used for outbound HTTP requests — both the initial configuration URL provided by the caller
 * and all endpoint URLs returned by the platform — is validated via {@link #validateExternalUrl(String)}
 * before use. This ensures the server only connects to legitimate, publicly routable HTTPS hosts,
 * while still allowing localhost-based HTTP endpoints for local development.
 */
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
     * Performs the full LTI 1.3 dynamic registration flow.
     * <p>
     * Steps:
     * <ol>
     * <li>Fetch the platform's OpenID Connect configuration from {@code openIdConfigurationUrl}.</li>
     * <li>Validate all endpoint URLs the platform returned (authorization, token, JWKS, registration).</li>
     * <li>POST Artemis' client registration to the platform's registration endpoint.</li>
     * <li>Persist the resulting {@link LtiPlatformConfiguration} and generate a JWKS key pair for the new registration.</li>
     * </ol>
     *
     * @param openIdConfigurationUrl the platform-provided URL that serves the OpenID Connect discovery document
     * @param registrationToken      a one-time bearer token used to authenticate the registration POST request (may be {@code null})
     * @throws BadRequestAlertException if any URL is invalid, any required field is missing, or the platform rejects the registration
     * @throws IllegalStateException    if the platform returns an HTTP error during the flow
     */
    public void performDynamicRegistration(String openIdConfigurationUrl, String registrationToken) {
        try {
            // Step 1: Fetch the platform's OpenID configuration (the URL is validated inside getLti13PlatformConfiguration)
            Lti13PlatformConfiguration platformConfiguration = getLti13PlatformConfiguration(openIdConfigurationUrl);

            String clientRegistrationId = "artemis-" + UUID.randomUUID();

            // Verify that all required endpoint URLs are present in the platform response
            if (platformConfiguration.authorizationEndpoint() == null || platformConfiguration.tokenEndpoint() == null || platformConfiguration.jwksUri() == null
                    || platformConfiguration.registrationEndpoint() == null) {
                throw new BadRequestAlertException("Invalid platform configuration", "LTI", "invalidPlatformConfiguration");
            }

            // Step 2: Validate all platform-provided URLs before using them for outbound requests or persisting them.
            // This is critical because the platform response is untrusted external input — each URL could potentially
            // point to an internal service if not checked.
            validateExternalUrl(platformConfiguration.authorizationEndpoint());
            validateExternalUrl(platformConfiguration.tokenEndpoint());
            validateExternalUrl(platformConfiguration.jwksUri());
            validateExternalUrl(platformConfiguration.registrationEndpoint());

            // Step 3: Register Artemis as an LTI tool on the platform
            var clientRegistrationResponse = postClientRegistrationToPlatform(platformConfiguration.registrationEndpoint(), clientRegistrationId, registrationToken);

            // Step 4: Persist configuration and generate JWKS key pair
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
     * Validates that the given URL is safe for outbound HTTP requests during LTI registration.
     * <p>
     * Enforces the following rules:
     * <ul>
     * <li>The URL must be syntactically valid.</li>
     * <li>Only the {@code https} scheme is permitted.</li>
     * <li>The URL must contain a non-null host.</li>
     * <li><strong>All</strong> IP addresses the host resolves to (via {@link InetAddress#getAllByName})
     * must be public — none may be loopback, site-local, link-local, multicast, or otherwise
     * reserved (see {@link #isPrivateOrReservedAddress}).</li>
     * </ul>
     *
     * @param url the URL to validate
     * @throws BadRequestAlertException if the URL fails any of the above checks
     */
    private void validateExternalUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("Invalid URL format", "LTI", "invalidUrl");
        }

        String host = uri.getHost();

        if (host == null) {
            throw new BadRequestAlertException("URL must contain a valid host", "LTI", "invalidUrl");
        }

        String scheme = uri.getScheme();

        if (!isLocalDevelopmentHost(host) && !"https".equalsIgnoreCase(scheme)) {
            throw new BadRequestAlertException("Only HTTPS URLs are allowed for LTI configuration", "LTI", "invalidUrl");
        }

        if (isLocalDevelopmentHost(host)) {
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BadRequestAlertException("Only HTTP(S) URLs are allowed for localhost development hosts", "LTI", "invalidUrl");
            }
            return;
        }

        try {
            // Resolve all addresses for the host and reject if any points to a non-public range.
            // Using getAllByName (not getByName) is important: a hostname may resolve to multiple
            // IPs via DNS round-robin, and every single one must be checked.
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrReservedAddress(address)) {
                    throw new BadRequestAlertException("URLs pointing to internal or loopback addresses are not allowed", "LTI", "invalidUrl");
                }
            }
        }
        catch (UnknownHostException e) {
            throw new BadRequestAlertException("URL host could not be resolved", "LTI", "invalidUrl");
        }
    }

    private boolean isLocalDevelopmentHost(String host) {
        String normalizedHost = host.toLowerCase();
        return "localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost");
    }

    /**
     * Determines whether the given {@link InetAddress} belongs to a non-public IP range.
     * <p>
     * The following ranges are treated as non-public:
     * <ul>
     * <li>Loopback (127.0.0.0/8, ::1)</li>
     * <li>Site-local / private (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)</li>
     * <li>Link-local (169.254.0.0/16, fe80::/10)</li>
     * <li>Any-local / wildcard (0.0.0.0, ::)</li>
     * <li>Multicast (224.0.0.0/4, ff00::/8)</li>
     * <li>Shared / CGN address space (100.64.0.0/10, RFC 6598)</li>
     * <li>"This" network (0.0.0.0/8)</li>
     * <li>IPv6 Unique Local Addresses (fc00::/7)</li>
     * <li>IPv4-mapped IPv6 addresses (::ffff:x.x.x.x) — the embedded IPv4 address is extracted
     * and re-checked recursively</li>
     * <li>Deprecated IPv4-compatible IPv6 addresses (::x.x.x.x, RFC 4291) — handled the same way</li>
     * </ul>
     *
     * @param address the resolved address to check
     * @return {@code true} if the address falls into any non-public range listed above
     */
    private boolean isPrivateOrReservedAddress(InetAddress address) {
        // Standard JDK checks cover loopback, site-local (RFC 1918), link-local, wildcard, and multicast
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }

        byte[] addr = address.getAddress();

        // --- IPv4-specific checks ---
        if (addr.length == 4) {
            int firstOctet = addr[0] & 0xFF;
            int secondOctet = addr[1] & 0xFF;
            // 100.64.0.0/10 — Shared Address Space (CGN / RFC 6598), used by some cloud providers
            if (firstOctet == 100 && (secondOctet & 0xC0) == 64) {
                return true;
            }
            // 0.0.0.0/8 — "This" network (RFC 791)
            if (firstOctet == 0) {
                return true;
            }
        }

        // --- IPv6-specific checks ---
        if (addr.length == 16) {
            // Check for IPv4-mapped IPv6 (::ffff:x.x.x.x) — extract the IPv4 portion and re-check
            boolean prefixAllZeros = true;
            for (int i = 0; i < 10; i++) {
                if (addr[i] != 0) {
                    prefixAllZeros = false;
                    break;
                }
            }
            if (prefixAllZeros && (addr[10] & 0xFF) == 0xFF && (addr[11] & 0xFF) == 0xFF) {
                byte[] v4Addr = new byte[] { addr[12], addr[13], addr[14], addr[15] };
                try {
                    return isPrivateOrReservedAddress(InetAddress.getByAddress(v4Addr));
                }
                catch (UnknownHostException e) {
                    return true;
                }
            }
            // Deprecated IPv4-compatible IPv6 (::x.x.x.x, RFC 4291) — bytes 0-9 are zero
            // but bytes 10-11 are not 0xFF, still parsed by Java
            if (prefixAllZeros) {
                byte[] v4Addr = new byte[] { addr[12], addr[13], addr[14], addr[15] };
                try {
                    return isPrivateOrReservedAddress(InetAddress.getByAddress(v4Addr));
                }
                catch (UnknownHostException e) {
                    return true;
                }
            }
            // fc00::/7 — IPv6 Unique Local Addresses (RFC 4193)
            if ((addr[0] & 0xFE) == 0xFC) {
                return true;
            }
        }

        return false;
    }

    /**
     * Fetches the LTI 1.3 platform's OpenID Connect configuration from the given URL.
     * <p>
     * The URL is validated before the request is made. The response is deserialized into an
     * {@link Lti13PlatformConfiguration} containing the platform's authorization, token, JWKS,
     * and registration endpoints.
     *
     * @param openIdConfigurationUrl the platform-provided OpenID Connect discovery URL
     * @return the deserialized platform configuration
     * @throws BadRequestAlertException if the URL is invalid or the platform does not return a valid configuration
     */
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

    /**
     * Registers Artemis as an LTI 1.3 tool on the external platform by POSTing a client registration.
     * <p>
     * The registration endpoint URL is validated before the request is made. If a {@code registrationToken}
     * is provided, it is sent as a Bearer token in the Authorization header.
     *
     * @param registrationEndpoint the platform's registration endpoint URL
     * @param clientRegistrationId the unique client registration ID generated for this registration (e.g. "artemis-&lt;uuid&gt;")
     * @param registrationToken    the one-time bearer token for authenticating the request (may be {@code null})
     * @return the platform's response containing the assigned client ID and other registration details
     * @throws BadRequestAlertException if the URL is invalid or the platform rejects/does not respond to the registration
     */
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
            log.error("Could not register new client in external LMS at {}", registrationEndpoint);
        }

        if (registrationResponse == null) {
            throw new BadRequestAlertException("Could not register configuration in external LMS", "LTI", "postConfigurationFailed");
        }
        return registrationResponse;
    }

    /**
     * Creates a new {@link LtiPlatformConfiguration} entity from the platform's OpenID configuration
     * and the client registration response.
     * <p>
     * Maps the platform's endpoint URLs and the assigned client ID into a persistable entity.
     *
     * @param registrationId             the unique registration ID generated for this registration
     * @param platformConfiguration      the platform's OpenID Connect configuration (source of endpoint URLs)
     * @param clientRegistrationResponse the platform's response to the client registration POST (source of client ID)
     * @return a fully populated {@link LtiPlatformConfiguration} ready to be persisted
     */
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
