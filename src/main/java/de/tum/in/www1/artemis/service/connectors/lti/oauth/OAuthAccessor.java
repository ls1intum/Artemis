package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Properties of one User of an OAuthConsumer. Properties may be added freely,
 * e.g. to support extensions.
 *
 * @author John Kristian
 */
public class OAuthAccessor implements Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 5590788443138352999L;

    public final OAuthConsumer consumer;

    public String requestToken;

    public String accessToken;

    public String tokenSecret;

    public OAuthAccessor(OAuthConsumer consumer) {
        this.consumer = consumer;
        this.requestToken = null;
        this.accessToken = null;
        this.tokenSecret = null;
    }

    private final Map<String, Object> properties = new HashMap<>();

    @Override
    public OAuthAccessor clone() {
        try {
            return (OAuthAccessor) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

}
