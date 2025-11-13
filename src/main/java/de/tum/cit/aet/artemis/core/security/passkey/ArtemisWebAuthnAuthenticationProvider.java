package de.tum.cit.aet.artemis.core.security.passkey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.util.Assert;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * <p>
 * Adaption of the Spring Security WebAuthnAuthenticationProvider.
 * </p>
 * <p>
 * We need to adapt the AuthenticationProvider as we do not want to change the implementation of {@link de.tum.cit.aet.artemis.core.security.DomainUserDetailsService}, as this
 * would us require to
 * <ul>
 * <li>use a less concrete method to retrieve users than {@link de.tum.cit.aet.artemis.core.repository.UserRepository#findOneWithGroupsAndAuthoritiesByEmailAndInternal} to support
 * external users</li>
 * <li>create a {@link org.springframework.security.core.userdetails.User} with a temporary password <i>(as user creation fails with null as password, which can be erased after
 * creation)</i></li>
 * </ul>
 * </p>
 *
 * @see org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider
 */
public class ArtemisWebAuthnAuthenticationProvider implements AuthenticationProvider {

    private final WebAuthnRelyingPartyOperations relyingPartyOperations;

    private final UserRepository userRepository;

    private final PasskeyCredentialsRepository passkeyCredentialsRepository;

    /**
     * Creates a new instance.
     *
     * @param relyingPartyOperations       the {@link WebAuthnRelyingPartyOperations} to use. Cannot be null.
     * @param userRepository               the {@link UserRepository} to use. Cannot be null.
     * @param passkeyCredentialsRepository the {@link PasskeyCredentialsRepository} to use. Cannot be null.
     */
    public ArtemisWebAuthnAuthenticationProvider(WebAuthnRelyingPartyOperations relyingPartyOperations, UserRepository userRepository,
            PasskeyCredentialsRepository passkeyCredentialsRepository) {
        Assert.notNull(relyingPartyOperations, "relyingPartyOperations cannot be null");
        Assert.notNull(userRepository, "userRepository cannot be null");
        Assert.notNull(passkeyCredentialsRepository, "passkeyCredentialsRepository cannot be null");
        this.relyingPartyOperations = relyingPartyOperations;
        this.userRepository = userRepository;
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        WebAuthnAuthenticationRequestToken webAuthnRequest = (WebAuthnAuthenticationRequestToken) authentication;
        try {
            String credentialId = webAuthnRequest.getWebAuthnRequest().getPublicKey().getId();

            PublicKeyCredentialUserEntity userEntity = this.relyingPartyOperations.authenticate(webAuthnRequest.getWebAuthnRequest());
            String username = userEntity.getName();
            Optional<User> user = this.userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
            if (user.isEmpty()) {
                throw new BadCredentialsException("User " + username + " was not found in the database");
            }

            Map<String, Object> details = createAuthenticationDetailsWithPasskeyApproval(credentialId);

            WebAuthnAuthentication auth = new WebAuthnAuthentication(userEntity, user.get().getGrantedAuthorities());
            auth.setDetails(details);

            return auth;
        }
        catch (RuntimeException ex) {
            throw new BadCredentialsException(ex.getMessage(), ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WebAuthnAuthenticationRequestToken.class.isAssignableFrom(authentication);
    }

    /**
     * Creates authentication details containing the passkey super admin approval status.
     *
     * @param credentialId to check for super admin approval
     * @return a map containing the authentication details with the passkey super admin approval status
     */
    private Map<String, Object> createAuthenticationDetailsWithPasskeyApproval(String credentialId) {
        Optional<PasskeyCredential> credential = this.passkeyCredentialsRepository.findByCredentialId(credentialId);
        boolean isPasskeyApproved = credential.map(PasskeyCredential::isSuperAdminApproved).orElse(false);
        Map<String, Object> details = new HashMap<>();
        details.put(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, isPasskeyApproved);
        return details;
    }

}
