package de.tum.in.www1.artemis.security.lti;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.lti.Scopes;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import uk.ac.ox.ctl.lti13.TokenRetriever;

/**
 * This class is responsible to retrieve access tokens from an LTI 1.3 platform of a specific ClientRegistration.
 *
 * Per default, the (correct) TokenRetriever Implementation of lti13-spring-security is used to query a token.
 * However, the token endpoint of OpenOLAT (v16.0.0) LTI 1.3 Beta behaves sightly different as specified in LTI 1.3.
 * The default TokenRetriever fails to query an access token from OpenOLAT in some cases. To handle this, a fallback
 * token retriever is used to deal with this special situation and query an access token from OpenOLAT.
 *
 */
@Component
public class Lti13TokenRetriever {

    private TokenRetriever defaultRetriever;

    private Lti13OLATFallbackTokenRetriever olatFallbackTokenRetriever;

    private Logger log = LoggerFactory.getLogger(Lti13TokenRetriever.class);

    public Lti13TokenRetriever(OAuth2JWKSService keyPairService) {
        this.defaultRetriever = new TokenRetriever(keyPairService);
        this.olatFallbackTokenRetriever = new Lti13OLATFallbackTokenRetriever(keyPairService);
    }

    /**
     * Returns an access token for a specific client to be used to authenticate further communication with the related
     * LTI 1.3 platform of that client. The queried access token (if there is any) will be valid for the requested scopes.
     *
     * As of LTI 1.3 specification there is no use for a LTI resource link (targetLinkUri) in the process of token retrieval.
     * However, OpenOLAT requires this information (as of v16.0.0). For any other system the default TokenRetriever will be used
     * to query a token - in this case the value of targetLinkUri won't change anything.
     * @param client to query a token for.
     * @param targetLinkUri of the related Lti13ResourceLaunch.
     * @param scopes to ask access for.
     * @return the access token to be used to authenticate requests to the client's LTI 1.3 platform.
     */
    public OAuth2AccessTokenResponse getToken(ClientRegistration client, String targetLinkUri, String... scopes) {
        OAuth2AccessTokenResponse token = null;
        try {
            log.info("Trying to retrieve access token with default token retriever");
            token = defaultRetriever.getToken(client, scopes);
        }
        catch (Exception e) {
            String message = "Could not retrieve access token for client " + client.getClientId() + " with default TokenRetriever: " + e.getMessage();
            log.error(message);
        }

        if (token == null) {
            try {
                log.info("Aiming to retrieve access token with fallback token retriever");
                token = olatFallbackTokenRetriever.getToken(client, targetLinkUri, Scopes.AGS_SCORE);
            }
            catch (Exception ex) {
                String message = "Could not retrieve access token for client " + client.getClientId() + " with FallbackTokenRetriever: " + ex.getMessage();
                log.error(message);
                return null;
            }
        }

        return token;
    }
}
