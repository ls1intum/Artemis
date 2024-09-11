package de.tum.cit.aet.artemis.theia;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_THEIA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.config.Constants;
import de.tum.cit.aet.artemis.service.theia.TheiaInfoContributor;

@Profile(PROFILE_THEIA)
class TheiaInfoContributorTest {

    @Value("${theia.portal-url}")
    private String expectedValue;

    TheiaInfoContributor theiaInfoContributor;

    @Test
    void testContribute() {
        Info.Builder builder = new Info.Builder();
        theiaInfoContributor = new TheiaInfoContributor();
        theiaInfoContributor.contribute(builder);

        Info info = builder.build();
        assertThat(info.getDetails().get(Constants.THEIA_PORTAL_URL)).isEqualTo(expectedValue);
    }
}
