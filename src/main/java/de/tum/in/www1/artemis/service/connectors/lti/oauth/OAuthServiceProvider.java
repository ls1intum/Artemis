package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.io.Serial;
import java.io.Serializable;

/**
 * Properties of an OAuth Service Provider.
 *
 * @author John Kristian
 */
public record OAuthServiceProvider(String requestTokenURL, String userAuthorizationURL, String accessTokenURL) implements Serializable {

    @Serial
    private static final long serialVersionUID = 3306534392621038574L;

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OAuthServiceProvider other = (OAuthServiceProvider) obj;
        if (accessTokenURL == null) {
            if (other.accessTokenURL != null)
                return false;
        }
        else if (!accessTokenURL.equals(other.accessTokenURL))
            return false;
        if (requestTokenURL == null) {
            if (other.requestTokenURL != null)
                return false;
        }
        else if (!requestTokenURL.equals(other.requestTokenURL))
            return false;
        if (userAuthorizationURL == null) {
            return other.userAuthorizationURL == null;
        }
        else
            return userAuthorizationURL.equals(other.userAuthorizationURL);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((accessTokenURL == null) ? 0 : accessTokenURL.hashCode());
        result = prime * result + ((requestTokenURL == null) ? 0 : requestTokenURL.hashCode());
        result = prime * result + ((userAuthorizationURL == null) ? 0 : userAuthorizationURL.hashCode());
        return result;
    }
}
