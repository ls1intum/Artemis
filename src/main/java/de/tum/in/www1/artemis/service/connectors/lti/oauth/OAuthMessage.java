package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.in.www1.artemis.service.connectors.lti.oauth.signature.OAuthSignatureMethod;

/**
 * A request or response message used in the OAuth protocol.
 * <p>
 * The parameters in this class are not percent-encoded. Methods like
 * OAuthClient.invoke and OAuthResponseMessage.completeParameters are
 * responsible for percent-encoding parameters before transmission and decoding
 * them after reception.
 *
 * @author John Kristian
 */
public class OAuthMessage {

    public OAuthMessage(String method, String URL, Collection<? extends Map.Entry<String, String>> parameters) {
        this.method = method;
        this.URL = URL;
        if (parameters == null) {
            this.parameters = new ArrayList<>();
        }
        else {
            this.parameters = new ArrayList<>(parameters.size());
            for (Map.Entry<String, String> p : parameters) {
                this.parameters.add(new OAuth.Parameter(toString(p.getKey()), toString(p.getValue())));
            }
        }
    }

    public String method;

    public String URL;

    private final List<Map.Entry<String, String>> parameters;

    private Map<String, String> parameterMap;

    private boolean parametersAreComplete = false;

    private final List<Map.Entry<String, String>> headers = new ArrayList<>();

    public String toString() {
        return "OAuthMessage(" + method + ", " + URL + ", " + parameters + ")";
    }

    /** A caller is about to get a parameter. */
    private void beforeGetParameter() {
        if (!parametersAreComplete) {
            completeParameters();
            parametersAreComplete = true;
        }
    }

    /**
     * Finish adding parameters; for example read an HTTP response body and
     * parse parameters from it.
     */
    protected void completeParameters() {
    }

    public List<Map.Entry<String, String>> getParameters() throws IOException {
        beforeGetParameter();
        return Collections.unmodifiableList(parameters);
    }

    public void addParameter(Map.Entry<String, String> parameter) {
        parameters.add(parameter);
        parameterMap = null;
    }

    public String getParameter(String name) {
        return getParameterMap().get(name);
    }

    public String getConsumerKey() {
        return getParameter(OAuth.OAUTH_CONSUMER_KEY);
    }

    public String getToken() {
        return getParameter(OAuth.OAUTH_TOKEN);
    }

    public String getSignatureMethod() {
        return getParameter(OAuth.OAUTH_SIGNATURE_METHOD);
    }

    public String getSignature() {
        return getParameter(OAuth.OAUTH_SIGNATURE);
    }

    protected Map<String, String> getParameterMap() {
        beforeGetParameter();
        if (parameterMap == null) {
            parameterMap = OAuth.newMap(parameters);
        }
        return parameterMap;
    }

    /** All HTTP headers.  You can add headers to this list. */
    public final List<Map.Entry<String, String>> getHeaders() {
        return headers;
    }

    /**
     * Verify that the required parameter names are contained in the actual
     * collection.
     *
     * @throws OAuthProblemException
     *                 one or more parameters are absent.
     */
    public void requireParameters(String... names) throws OAuthProblemException {
        Set<String> present = getParameterMap().keySet();
        List<String> absent = new ArrayList<>();
        for (String required : names) {
            if (!present.contains(required)) {
                absent.add(required);
            }
        }
        if (!absent.isEmpty()) {
            OAuthProblemException problem = new OAuthProblemException(OAuth.Problems.PARAMETER_ABSENT);
            problem.setParameter(OAuth.Problems.OAUTH_PARAMETERS_ABSENT, OAuth.percentEncode(absent));
            throw problem;
        }
    }

    /**
     * Add a signature to the message.
     *
     */
    public void sign(OAuthAccessor accessor) throws IOException, OAuthException, URISyntaxException {
        OAuthSignatureMethod.newSigner(this, accessor).sign(this);
    }

    /**
     * Parse the parameters from an OAuth Authorization or WWW-Authenticate
     * header. The realm is included as a parameter. If the given header doesn't
     * start with "OAuth ", return an empty list.
     */
    public static List<OAuth.Parameter> decodeAuthorization(String authorization) {
        List<OAuth.Parameter> into = new ArrayList<>();
        if (authorization != null) {
            Matcher m = AUTHORIZATION.matcher(authorization);
            if (m.matches()) {
                if (AUTH_SCHEME.equalsIgnoreCase(m.group(1))) {
                    for (String nvp : m.group(2).split("\\s*,\\s*")) {
                        m = NVP.matcher(nvp);
                        if (m.matches()) {
                            String name = OAuth.decodePercent(m.group(1));
                            String value = OAuth.decodePercent(m.group(2));
                            into.add(new OAuth.Parameter(name, value));
                        }
                    }
                }
            }
        }
        return into;
    }

    public static final String AUTH_SCHEME = "OAuth";

    public static final String GET = "GET";

    public static final String POST = "POST";

    public static final String PUT = "PUT";

    public static final String DELETE = "DELETE";

    private static final Pattern AUTHORIZATION = Pattern.compile("\\s*(\\w*)\\s+(.*)");

    private static final Pattern NVP = Pattern.compile("(\\S*)\\s*=\\s*\"([^\"]*)\"");

    private static String toString(Object from) {
        return (from == null) ? null : from.toString();
    }

}
