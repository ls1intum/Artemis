package de.tum.cit.aet.artemis.lti.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OIDCInitiationRegistrationResolver;

/**
 * Spring Security 7 compatible replacement for the library's {@code PathOIDCInitiationRegistrationResolver},
 * which was incompatible because it used the removed {@code AntPathRequestMatcher}.
 * <p>
 * This class uses {@link PathPatternRequestMatcher} (Spring Security 7) to resolve the
 * {@code registrationId} path variable from LTI login initiation requests.
 */
public class Lti13PathRegistrationResolver implements OIDCInitiationRegistrationResolver {

    private static final String REGISTRATION_ID_URI_VARIABLE_NAME = "registrationId";

    private final RequestMatcher authorizationRequestMatcher;

    /**
     * Creates a resolver that extracts the registrationId from the request URI.
     *
     * @param authorizationRequestBaseUri the base URI for LTI login initiation (e.g., "/api/lti/public/lti13/login_initiation")
     */
    public Lti13PathRegistrationResolver(String authorizationRequestBaseUri) {
        Assert.hasText(authorizationRequestBaseUri, "authorizationRequestBaseUri cannot be empty");
        this.authorizationRequestMatcher = PathPatternRequestMatcher.pathPattern(authorizationRequestBaseUri + "/{" + REGISTRATION_ID_URI_VARIABLE_NAME + "}");
    }

    @Override
    public String resolve(HttpServletRequest request) {
        RequestMatcher.MatchResult result = this.authorizationRequestMatcher.matcher(request);
        if (result.isMatch()) {
            return result.getVariables().get(REGISTRATION_ID_URI_VARIABLE_NAME);
        }
        return null;
    }
}
