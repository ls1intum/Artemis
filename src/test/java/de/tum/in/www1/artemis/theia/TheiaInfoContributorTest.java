package de.tum.in.www1.artemis.theia;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_THEIA;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.theia.TheiaInfoContributor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.context.annotation.Profile;

@Profile(PROFILE_THEIA)
class TheiaInfoContributorTest {

    @Value("${theia.portal-url}")
    private String expectedValue;

    TheiaInfoContributor theiaInfoContributor;

    @Test
    void testContribute() {
        Info.Builder builder = new Info.Builder();
        theiaInfoContributor = new TheiaInfoContributor();
        try {
            theiaInfoContributor.contribute(builder);
        } catch (NullPointerException e) {
        }

        Info info = builder.build();
        assertThat(info.getDetails().get(Constants.THEIA_PORTAL_URL)).isEqualTo(expectedValue);

    }
}
