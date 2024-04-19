package de.tum.in.www1.artemis.web.filter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

public class ArtemisBasicAuthenticationFilter extends BasicAuthenticationFilter {

    public ArtemisBasicAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        if (request.getRequestURI().startsWith("/git/")) {
            return true;
        }
        return super.shouldNotFilter(request);
    }
}
