package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * An OAuth-related problem, described using a set of named parameters. One
 * parameter identifies the basic problem, and the others provide supplementary
 * diagnostic information. This can be used to capture information from a
 * response that conforms to the OAuth <a
 * href="http://wiki.oauth.net/ProblemReporting">Problem Reporting
 * extension</a>.
 *
 * @author John Kristian
 */
public class OAuthProblemException extends OAuthException {

    public static final String OAUTH_PROBLEM = "oauth_problem";

    /** The name of a parameter whose value is the HTTP request. */
    public static final String HTTP_REQUEST = "HTTP request";

    /** The name of a parameter whose value is the HTTP response. */
    public static final String HTTP_RESPONSE = "HTTP response";

    /** The name of a parameter whose value is the HTTP response status code. */
    public static final String HTTP_STATUS_CODE = "HTTP status";

    /** The name of a parameter whose value is the OAuth signature base string. */
    public static final String SIGNATURE_BASE_STRING = OAuth.OAUTH_SIGNATURE + " base string";

    /** The name of a parameter whose value is the request URL. */
    public static final String URL = "URL";

    public OAuthProblemException(String problem) {
        super(problem);
        if (problem != null) {
            parameters.put(OAUTH_PROBLEM, problem);
        }
    }

    private final Map<String, Object> parameters = new HashMap<>();

    @Override
    public String getMessage() {
        String msg = super.getMessage();
        if (msg != null)
            return msg;
        msg = getProblem();
        if (msg != null)
            return msg;
        Object response = getParameters().get(HTTP_RESPONSE);
        if (response != null) {
            msg = response.toString();
            int eol = msg.indexOf("\n");
            if (eol < 0) {
                eol = msg.indexOf("\r");
            }
            if (eol >= 0) {
                msg = msg.substring(0, eol);
            }
            msg = msg.trim();
            if (msg.length() > 0) {
                return msg;
            }
        }
        response = getHttpStatusCode();
        return HTTP_STATUS_CODE + " " + response;
    }

    public void setParameter(String name, Object value) {
        getParameters().put(name, value);
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getProblem() {
        return (String) getParameters().get(OAUTH_PROBLEM);
    }

    /**
     * get the http status code of the exception
     *
     * @return the status code as int
     */
    public int getHttpStatusCode() {
        Object code = getParameters().get(HTTP_STATUS_CODE);
        if (code == null) {
            return 200;
        }
        else if (code instanceof Number) { // the usual case
            return ((Number) code).intValue();
        }
        else {
            return Integer.parseInt(code.toString());
        }
    }

    @Override
    public String toString() {
        final StringBuilder s = new StringBuilder(super.toString());
        try {
            final String eol = System.getProperty("line.separator", "\n");
            final Map<String, Object> parameters = getParameters();
            for (String key : new String[] { OAuth.Problems.OAUTH_PROBLEM_ADVICE, URL, SIGNATURE_BASE_STRING }) {
                Object value = parameters.get(key);
                if (value != null)
                    s.append(eol).append(key).append(": ").append(value);
            }
            Object msg = parameters.get(HTTP_REQUEST);
            if ((msg != null))
                s.append(eol).append(">>>>>>>> ").append(HTTP_REQUEST).append(":").append(eol).append(msg);
            msg = parameters.get(HTTP_RESPONSE);
            if (msg != null) {
                s.append(eol).append("<<<<<<<< ").append(HTTP_RESPONSE).append(":").append(eol).append(msg);
            }
            else {
                for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                    String key = parameter.getKey();
                    if (OAuth.Problems.OAUTH_PROBLEM_ADVICE.equals(key) || URL.equals(key) || SIGNATURE_BASE_STRING.equals(key) || HTTP_REQUEST.equals(key)
                            || HTTP_RESPONSE.equals(key))
                        continue;
                    s.append(eol).append(key).append(": ").append(parameter.getValue());
                }
            }
        }
        catch (Exception ignored) {
        }
        return s.toString();
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
