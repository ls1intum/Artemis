package de.tum.cit.aet.artemis.core.security.webauthn;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.Assert;

import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;

/**
 * An {@link AuthenticationSuccessHandler}, that sets a JWT token in the response and writes a JSON response with the redirect
 * URL and an authenticated status similar to:
 * <br>
 * Implementation Oriented at {@link org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler}, see implementation for more information
 */
public final class ArtemisHttpMessageConverterAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private HttpMessageConverter<Object> converter = new MappingJackson2HttpMessageConverter();

    private RequestCache requestCache = new HttpSessionRequestCache();

    private final JWTCookieService jwtCookieService;

    public ArtemisHttpMessageConverterAuthenticationSuccessHandler(JWTCookieService jwtCookieService) {
        this.jwtCookieService = jwtCookieService;
    }

    /**
     * @see org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler#setConverter
     *
     * @param converter the {@link GenericHttpMessageConverter} to use. Cannot be null.
     */
    public void setConverter(HttpMessageConverter<Object> converter) {
        Assert.notNull(converter, "converter cannot be null");
        this.converter = converter;
    }

    /**
     * @see org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler#setRequestCache
     *
     * @param requestCache the {@link RequestCache} to use. Cannot be null
     */
    public void setRequestCache(RequestCache requestCache) {
        Assert.notNull(requestCache, "requestCache cannot be null");
        this.requestCache = requestCache;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        final SavedRequest savedRequest = this.requestCache.getRequest(request, response);
        final String redirectUrl = (savedRequest != null) ? savedRequest.getRedirectUrl() : request.getContextPath() + "/";
        this.requestCache.removeRequest(request, response);

        boolean rememberMe = true; // means that the JWT token will be valid for a longer time (=> less often required to authenticate)
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        this.converter.write(new AuthenticationSuccess(redirectUrl), MediaType.APPLICATION_JSON, new ServletServerHttpResponse(response));
    }

    /**
     * @see org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler.AuthenticationSuccess
     */
    public static final class AuthenticationSuccess {

        private final String redirectUrl;

        private AuthenticationSuccess(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }

        public String getRedirectUrl() {
            return this.redirectUrl;
        }

        public boolean isAuthenticated() {
            return true;
        }

    }

}
