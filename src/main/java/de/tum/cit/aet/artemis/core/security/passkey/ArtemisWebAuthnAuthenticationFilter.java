package de.tum.cit.aet.artemis.core.security.passkey;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;

import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;

/**
 * We want to set the custom {@link ArtemisHttpMessageConverterAuthenticationSuccessHandler} here to make sure the JWT token is set in the response
 *
 * @see org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter
 */
public class ArtemisWebAuthnAuthenticationFilter extends WebAuthnAuthenticationFilter {

    public ArtemisWebAuthnAuthenticationFilter(HttpMessageConverter<Object> converter, JWTCookieService jwtCookieService,
            PublicKeyCredentialRequestOptionsRepository publicKeyCredentialRequestOptionsRepository) {
        super();
        setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        setRequestOptionsRepository(publicKeyCredentialRequestOptionsRepository);
        setAuthenticationFailureHandler(new AuthenticationEntryPointFailureHandler(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        setAuthenticationSuccessHandler(new ArtemisHttpMessageConverterAuthenticationSuccessHandler(converter, jwtCookieService));
    }

}
