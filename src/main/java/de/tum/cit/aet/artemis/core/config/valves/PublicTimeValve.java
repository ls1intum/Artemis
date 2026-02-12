package de.tum.cit.aet.artemis.core.config.valves;

import java.io.IOException;
import java.time.Instant;

import jakarta.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

/**
 * Ultra-fast public time endpoint valve that handles /api/public/time at the Tomcat level.
 * Bypasses all Spring filters, interceptors, and MVC processing for minimal latency.
 * Used by the Artemis client to synchronize time with the server for accurate countdown timers and time-sensitive features (e.g. exam mode).
 */
public class PublicTimeValve extends ValveBase {

    private static final String TIME_PATH = "/api/public/time";

    private static final String GET = "GET";

    private static final String CONTENT_TYPE = "text/plain;charset=UTF-8";

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        if (TIME_PATH.equals(request.getRequestURI()) && GET.equals(request.getMethod())) {
            response.setStatus(200);
            response.setContentType(CONTENT_TYPE);
            response.getWriter().write(Instant.now().toString());
            return;
        }
        getNext().invoke(request, response);
    }
}
