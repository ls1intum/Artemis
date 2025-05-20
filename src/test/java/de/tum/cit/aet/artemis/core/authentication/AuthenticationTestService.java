package de.tum.cit.aet.artemis.core.authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * This service helps to generate authentication objects.
 */
@Service
@Profile(SPRING_PROFILE_TEST)
public class AuthenticationTestService {

    public Authentication createWebAuthnAuthentication(String username) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));

        PublicKeyCredentialUserEntity principal = mock(PublicKeyCredentialUserEntity.class);
        when(principal.getId()).thenReturn(new Bytes(username.getBytes(StandardCharsets.UTF_8)));
        when(principal.getName()).thenReturn(username);
        when(principal.getDisplayName()).thenReturn(username);

        return new WebAuthnAuthentication(principal, authorities);
    }

}
