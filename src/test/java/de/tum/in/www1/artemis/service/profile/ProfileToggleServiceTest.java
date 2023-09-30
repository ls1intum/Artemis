package de.tum.in.www1.artemis.service.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class ProfileToggleServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ProfileToggleService profileToggleService;

    @Autowired
    private Environment env;

    @Test
    void getEnabledProfilesReturnsCorrectProfiles() {
        // On this single instance that is used during the test, the profiles of that instance are equal to all available profiles
        assertThat(profileToggleService.enabledProfiles()).containsExactlyInAnyOrder(env.getActiveProfiles());
    }

}
