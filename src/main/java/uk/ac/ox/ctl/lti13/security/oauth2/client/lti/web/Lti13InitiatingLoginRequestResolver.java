package uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Spring Framework 7 / Spring Boot 4 compatible drop-in replacement for
 * {@link OIDCInitiatingLoginRequestResolver} from {@code uk.ac.ox.ctl:spring-security-lti13:0.3.4}.
 * <p>
 * The upstream resolver calls {@code UriComponentsBuilder.fromHttpUrl(String)} in its
 * {@code expandRedirectUri} method. That overload was deprecated in Spring Framework 6 and removed
 * in Spring Framework 7, so the upstream class throws {@link NoSuchMethodError} on every LTI 1.3
 * Step 1 (third-party initiated login) call once Artemis runs on Spring Boot 4. See issue #12739
 * and the open upstream PR <a href="https://github.com/oxctl/spring-security-lti13/pull/60">oxctl/spring-security-lti13#60</a>.
 * <p>
 * This class is a verbatim copy of {@code OIDCInitiatingLoginRequestResolver} with the single line
 * changed to {@code UriComponentsBuilder.fromUriString(...)}, matching the upstream PR. It lives in
 * the same package as the upstream class so it can throw {@code InvalidClientRegistrationIdException}
 * (package-private upstream) and so the filter's 404 vs 500 bucketing keeps working unchanged.
 * <p>
 * Remove this class and restore {@code new OIDCInitiatingLoginRequestResolver(...)} in
 * {@code CustomLti13Configurer.configureInitiationFilter} once a Spring 7-compatible release of
 * {@code spring-security-lti13} is published.
 */
public class Lti13InitiatingLoginRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final OIDCInitiationRegistrationResolver registrationResolver;

    private final StringKeyGenerator stateGenerator = KeyGenerators.string();

    public Lti13InitiatingLoginRequestResolver(ClientRegistrationRepository clientRegistrationRepository, OIDCInitiationRegistrationResolver registrationResolver) {
        Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
        Assert.notNull(registrationResolver, "registrationResolver cannot be null");
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.registrationResolver = registrationResolver;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String registrationId = this.registrationResolver.resolve(request);
        return resolve(request, registrationId, "login");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        if (registrationId == null) {
            return null;
        }
        return resolve(request, registrationId, "login");
    }

    private OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId, String redirectUriAction) {
        if (registrationId == null) {
            return null;
        }

        ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId(registrationId);
        if (clientRegistration == null) {
            throw new InvalidClientRegistrationIdException("No Client Registration found with ID: " + registrationId);
        }

        OAuth2AuthorizationRequest.Builder builder;
        if (LTIAuthorizationGrantType.IMPLICIT.equals(clientRegistration.getAuthorizationGrantType())) {
            builder = OAuth2AuthorizationRequest.authorizationCode();
        }
        else {
            throw new IllegalArgumentException("Invalid Authorization Grant Type (" + clientRegistration.getAuthorizationGrantType().getValue()
                    + ") for Client Registration with Id: " + clientRegistration.getRegistrationId());
        }

        String iss = request.getParameter("iss");
        if (iss == null) {
            throw new InvalidInitiationRequestException("Required parameter iss was not supplied.");
        }

        String loginHint = request.getParameter("login_hint");
        if (loginHint == null) {
            throw new InvalidInitiationRequestException("Required parameter login_hint was not supplied.");
        }

        String targetLinkUri = request.getParameter("target_link_uri");
        if (targetLinkUri == null) {
            throw new InvalidInitiationRequestException("Required parameter target_link_uri was not supplied");
        }

        String clientId = request.getParameter("client_id");
        if (clientId != null && !clientId.equals(clientRegistration.getClientId())) {
            throw new IllegalArgumentException("Parameter client_id (" + clientId + ") doesn't match the configured registration (" + clientRegistration.getClientId() + ").");
        }

        String redirectUriStr = expandRedirectUri(request, clientRegistration, redirectUriAction);

        Map<String, Object> additionalParameters = new HashMap<>();
        additionalParameters.put(OAuth2ParameterNames.REGISTRATION_ID, clientRegistration.getRegistrationId());
        additionalParameters.put(OAuth2ParameterNames.RESPONSE_TYPE, "id_token");
        additionalParameters.put("login_hint", loginHint);
        additionalParameters.put("response_mode", "form_post");
        additionalParameters.put("nonce", UUID.randomUUID().toString());
        additionalParameters.put("prompt", "none");

        String ltiMessageHint = request.getParameter("lti_message_hint");
        if (ltiMessageHint != null) {
            additionalParameters.put("lti_message_hint", ltiMessageHint);
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(StateAuthorizationRequestRepository.REMOTE_IP, request.getRemoteAddr());

        return builder.clientId(clientRegistration.getClientId()).authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri()).redirectUri(redirectUriStr)
                .scopes(clientRegistration.getScopes()).state(this.stateGenerator.generateKey()).additionalParameters(additionalParameters).attributes(attributes).build();
    }

    private String expandRedirectUri(HttpServletRequest request, ClientRegistration clientRegistration, String action) {
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("registrationId", clientRegistration.getRegistrationId());
        // The single behavioural change vs. upstream 0.3.4: fromHttpUrl(String) was removed in Spring Framework 7,
        // fromUriString(String) is the documented replacement and behaves identically for fully-qualified http(s) URLs.
        String baseUrl = UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request)).replaceQuery(null).replacePath(request.getContextPath()).build().toUriString();
        uriVariables.put("baseUrl", baseUrl);
        if (action != null) {
            uriVariables.put("action", action);
        }
        return UriComponentsBuilder.fromUriString(clientRegistration.getRedirectUri()).buildAndExpand(uriVariables).toUriString();
    }
}
