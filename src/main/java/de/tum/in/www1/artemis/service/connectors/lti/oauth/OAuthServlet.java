package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import jakarta.servlet.http.HttpServletRequest;

public class OAuthServlet {

    /**
     * Extract the parts of the given request that are relevant to OAuth.
     * Parameters include OAuth Authorization headers and the usual request
     * parameters in the query string and/or form encoded body. The header
     * parameters come first, followed by the rest in the order they came from
     * request.getParameterMap().
     *
     * @param request
     * @param URL
     *                    the official URL of this service; that is the URL a legitimate
     *                    client would use to compute the digital signature. If this
     *                    parameter is null, this method will try to reconstruct the URL
     *                    from the HTTP request; which may be wrong in some cases.
     * @return the OAuthMessage object that was part of the request
     */
    public static OAuthMessage getMessage(HttpServletRequest request, String URL) {
        if (URL == null) {
            URL = request.getRequestURL().toString();
        }
        int q = URL.indexOf('?');
        if (q >= 0) {
            URL = URL.substring(0, q);
            // The query string parameters will be included in
            // the result from getParameters(request).
        }
        return new HttpRequestMessage(request, URL);
    }

    /**
     * Reconstruct the requested URL, complete with query string (if any).
     *
     * @param request
     * @return the url that was used in the given request
     */
    public static String getRequestURL(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String queryString = request.getQueryString();
        if (queryString != null) {
            url.append("?").append(queryString);
        }
        return url.toString();
    }

}
