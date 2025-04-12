package de.tum.cit.aet.artemis.core.security.webauthn;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.util.Assert;

import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;

/**
 * @see org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter
 */
public class ArtemisWebAuthnAuthenticationFilter extends WebAuthnAuthenticationFilter {

    private static final Logger log = LoggerFactory.getLogger(ArtemisWebAuthnAuthenticationFilter.class);

    public ArtemisWebAuthnAuthenticationFilter(HttpMessageConverter<Object> converter, JWTCookieService jwtCookieService) {
        super();
        setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        setAuthenticationFailureHandler(new AuthenticationEntryPointFailureHandler(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        setAuthenticationSuccessHandler(new ArtemisHttpMessageConverterAuthenticationSuccessHandler(converter, jwtCookieService));
    }

    private GenericHttpMessageConverter<Object> converter = new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json().modules(new WebauthnJackson2Module()).build());

    private PublicKeyCredentialRequestOptionsRepository requestOptionsRepository = new HttpSessionPublicKeyCredentialRequestOptionsRepository();

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {

        log.debug("Attempting to authenticate");
        // Log request details
        log.warn("Request Origin: {}", request.getHeader("Origin"));
        log.info("Request Method: {}", request.getMethod());
        log.info("Request URI: {}", request.getRequestURI());
        request.getHeaderNames().asIterator().forEachRemaining(header -> log.info("Request Header: {} = {}", header, request.getHeader(header)));
        request.getParameterMap().forEach((key, value) -> log.info("Request Parameter: {} = {}", key, String.join(", ", value)));

        ServletServerHttpRequest httpRequest = new ServletServerHttpRequest(request);
        ResolvableType resolvableType = ResolvableType.forClassWithGenerics(PublicKeyCredential.class, AuthenticatorAssertionResponse.class);
        PublicKeyCredential<AuthenticatorAssertionResponse> publicKeyCredential = null;
        try {
            publicKeyCredential = (PublicKeyCredential<AuthenticatorAssertionResponse>) this.converter.read(resolvableType.getType(), getClass(), httpRequest);
        }
        catch (Exception ex) {
            throw new BadCredentialsException("Unable to authenticate the PublicKeyCredential", ex);
        }
        PublicKeyCredentialRequestOptions requestOptions = this.requestOptionsRepository.load(request);
        if (requestOptions == null) {
            throw new BadCredentialsException("Unable to authenticate the PublicKeyCredential. No PublicKeyCredentialRequestOptions found.");
        }
        this.requestOptionsRepository.save(request, response, null);
        RelyingPartyAuthenticationRequest authenticationRequest = new RelyingPartyAuthenticationRequest(requestOptions, publicKeyCredential);
        WebAuthnAuthenticationRequestToken token = new WebAuthnAuthenticationRequestToken(authenticationRequest);
        // Perform authentication
        Authentication authentication = getAuthenticationManager().authenticate(token);

        // Log response details
        log.info("Response Status: {}", response.getStatus());

        return authentication;
    }

    /**
     * Sets the {@link GenericHttpMessageConverter} to use for writing
     * {@code PublicKeyCredential<AuthenticatorAssertionResponse>} to the response. The
     * default is @{code MappingJackson2HttpMessageConverter}
     *
     * @param converter the {@link GenericHttpMessageConverter} to use. Cannot be null.
     */
    public void setConverter(GenericHttpMessageConverter<Object> converter) {
        Assert.notNull(converter, "converter cannot be null");
        this.converter = converter;
    }

    /**
     * Sets the {@link PublicKeyCredentialRequestOptionsRepository} to use. The default is
     * {@link HttpSessionPublicKeyCredentialRequestOptionsRepository}.
     *
     * @param requestOptionsRepository the
     *                                     {@link PublicKeyCredentialRequestOptionsRepository} to use. Cannot be null.
     */
    public void setRequestOptionsRepository(PublicKeyCredentialRequestOptionsRepository requestOptionsRepository) {
        Assert.notNull(requestOptionsRepository, "requestOptionsRepository cannot be null");
        this.requestOptionsRepository = requestOptionsRepository;
    }
}
