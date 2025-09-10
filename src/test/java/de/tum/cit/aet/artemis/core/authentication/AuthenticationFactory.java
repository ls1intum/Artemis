package de.tum.cit.aet.artemis.core.authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Factory for creating {@link org.springframework.security.core.Authentication} objects.
 */
public class AuthenticationFactory {

    private AuthenticationFactory() {
        // Prevent instantiation of this utility class
    }

    public static WebAuthnAuthentication createWebAuthnAuthentication(String username) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));

        PublicKeyCredentialUserEntity principal = mock(PublicKeyCredentialUserEntity.class);
        when(principal.getId()).thenReturn(new Bytes(username.getBytes(StandardCharsets.UTF_8)));
        when(principal.getName()).thenReturn(username);
        when(principal.getDisplayName()).thenReturn(username);

        return new WebAuthnAuthentication(principal, authorities);
    }

    public static UsernamePasswordAuthenticationToken createUsernamePasswordAuthentication(String username) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
        return new UsernamePasswordAuthenticationToken(username, username, authorities);
    }

    public static Saml2Authentication createSaml2Authentication(String username) {
        AuthenticatedPrincipal principal = () -> username;

        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
        return new Saml2Authentication(principal, username, authorities);
    }
}
