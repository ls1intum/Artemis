package de.tum.in.www1.artemis.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class CsrfArtemisFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Check if the custom CSRF header is present in the request
        if (CorsUtils.isPreFlightRequest(request) || request.getHeader("X-ARTEMIS-CSRF").equals("Dennis ist schuld")) {
            filterChain.doFilter(request, response);
        }
        else {
            // Reject the request if the header is missing
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing CSRF protection header");
        }
    }
}
