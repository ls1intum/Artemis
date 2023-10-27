package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.util.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * An HttpServletRequest, encapsulated as an OAuthMessage.
 *
 * @author John Kristian
 */
public class HttpRequestMessage extends OAuthMessage {

    public HttpRequestMessage(HttpServletRequest request, String URL) {
        super(request.getMethod(), URL, getParameters(request));
        copyHeaders(request, getHeaders());
    }

    private static void copyHeaders(HttpServletRequest request, Collection<Map.Entry<String, String>> into) {
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                Enumeration<String> values = request.getHeaders(name);
                if (values != null) {
                    while (values.hasMoreElements()) {
                        into.add(new OAuth.Parameter(name, values.nextElement()));
                    }
                }
            }
        }
    }

    /**
     * get the parameters of a request and return them in a list
     *
     * @param request
     * @return list of parameters of the given request
     */
    public static List<OAuth.Parameter> getParameters(HttpServletRequest request) {
        List<OAuth.Parameter> list = new ArrayList<>();
        for (Enumeration<String> headers = request.getHeaders("Authorization"); headers != null && headers.hasMoreElements();) {
            String header = headers.nextElement();
            for (OAuth.Parameter parameter : OAuthMessage.decodeAuthorization(header)) {
                if (!"realm".equalsIgnoreCase(parameter.getKey())) {
                    list.add(parameter);
                }
            }
        }
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                list.add(new OAuth.Parameter(name, value));
            }
        }
        return list;
    }

}
