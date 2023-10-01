package de.tum.in.www1.artemis.service.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

class ProfileToggleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProfileToggleService profileToggleService;

    @Autowired
    private Environment env;

    @Test
    void getEnabledProfilesReturnsCorrectProfiles() {
        profileToggleService.publishAvailableProfiles();

        assertThat(profileToggleService.enabledProfiles()).contains(env.getActiveProfiles());
    }

}
