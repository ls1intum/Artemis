package de.tum.cit.aet.artemis.core.security.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class CsrfArtemisFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CsrfArtemisFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info("origin: {}", request.getHeader("origin"));

        // Check if the custom CSRF header is present in the request
        if (request.getHeader("X-ARTEMIS-CSRF") != null) {
            filterChain.doFilter(request, response);
        }
        else {
            // Reject the request if the header is missing
            log.error("Missing CSRF protection header");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing CSRF protection header");
        }
    }
}
