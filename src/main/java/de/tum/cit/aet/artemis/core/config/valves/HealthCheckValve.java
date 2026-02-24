package de.tum.cit.aet.artemis.core.config.valves;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

/**
 * Ultra-fast public health check valve that handles /ping requests at the Tomcat level.
 * Bypasses all Spring filters, interceptors, and MVC processing for minimal latency.
 * Used by load balancers to detect unresponsive nodes.
 */
public class HealthCheckValve extends ValveBase {

    private static final String PING_PATH = "/ping";

    private static final String PONG_RESPONSE = "pong";

    private static final String CONTENT_TYPE = "text/plain;charset=UTF-8";

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        if (PING_PATH.equals(request.getRequestURI())) {
            response.setStatus(200);
            response.setContentType(CONTENT_TYPE);
            response.getWriter().write(PONG_RESPONSE);
            return;
        }
        getNext().invoke(request, response);
    }
}
