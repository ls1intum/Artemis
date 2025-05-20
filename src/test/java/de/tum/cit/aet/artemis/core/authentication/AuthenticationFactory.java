package de.tum.cit.aet.artemis.core.authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Factory for creating {@link org.springframework.security.core.Authentication} objects.
 */
public class AuthenticationFactory {

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

}
