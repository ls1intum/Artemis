package de.tum.cit.aet.artemis.core.domain;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.springframework.security.options.PublicKeyCredentialUserEntityProvider;
import com.webauthn4j.util.Base64UrlUtil;

import de.tum.cit.aet.artemis.core.security.DomainUserDetailsService;

// TODO not used in Artemis yet?
public class PublicKeyCredentialUserEntityProviderImpl implements PublicKeyCredentialUserEntityProvider {

    private final DomainUserDetailsService domainUserDetailsService;

    public PublicKeyCredentialUserEntityProviderImpl(DomainUserDetailsService domainUserDetailsService) {
        this.domainUserDetailsService = domainUserDetailsService;
    }

    @Override
    public PublicKeyCredentialUserEntity provide(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        String username = authentication.getName();
        UserDetails userEntity = domainUserDetailsService.loadUserByUsername(username);
        return new PublicKeyCredentialUserEntity(Base64UrlUtil.encode(userEntity.getUsername().getBytes()), // TODO use better handle here
                userEntity.getUsername(), userEntity.getUsername());
    }
}
