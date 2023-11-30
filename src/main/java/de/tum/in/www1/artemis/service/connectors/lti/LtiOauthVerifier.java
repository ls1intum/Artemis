package de.tum.in.www1.artemis.service.connectors.lti;

import jakarta.servlet.http.HttpServletRequest;

import de.tum.in.www1.artemis.service.connectors.lti.oauth.*;

public class LtiOauthVerifier {

    /**
     * verify the request based on the given secret
     *
     * @param request
     * @param secret
     * @return the verification result (success / error)
     * @throws LtiVerificationException
     */
    public LtiVerificationResult verify(HttpServletRequest request, String secret) throws LtiVerificationException {
        OAuthMessage oam = OAuthServlet.getMessage(request, OAuthServlet.getRequestURL(request));
        String oauth_consumer_key;
        try {
            oauth_consumer_key = oam.getConsumerKey();
        }
        catch (Exception e) {
            return new LtiVerificationResult(false, LtiError.BAD_REQUEST, "Unable to find consumer key in message");
        }

        SimpleOAuthValidator oav = new SimpleOAuthValidator();
        OAuthConsumer cons = new OAuthConsumer(null, oauth_consumer_key, secret, null);
        OAuthAccessor acc = new OAuthAccessor(cons);

        try {
            oav.validateMessage(oam, acc);
        }
        catch (Exception e) {
            return new LtiVerificationResult(false, LtiError.BAD_REQUEST, "Failed to validate: " + e.getLocalizedMessage());
        }
        return new LtiVerificationResult(true, new LtiLaunch(request));
    }
}
