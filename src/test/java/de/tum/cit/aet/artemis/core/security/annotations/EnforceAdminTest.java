package de.tum.cit.aet.artemis.core.security.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EnforceAdminTest {

    @Test
    void shouldValidateCurrentAdminAccount() {
        PreAuthorize preAuthorize = EnforceAdmin.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("hasRole('ADMIN')").contains("@userRepository.isAdmin(authentication.name)")
                .contains("@passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()");
    }
}
