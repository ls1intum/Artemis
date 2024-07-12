package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.context.annotation.Profile;

import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCInfoContributor;

@Profile(PROFILE_LOCALVC)
class LocalVCInfoContributorTest {

    LocalVCInfoContributor localVCInfoContributor;

    @Test
    void testContribute() {
        Info.Builder builder = new Info.Builder();
        localVCInfoContributor = new LocalVCInfoContributor();
        try {
            localVCInfoContributor.contribute(builder);
        }
        catch (NullPointerException e) {
        }

        Info info = builder.build();
        assertThat((Boolean) info.getDetails().get("versionControlAccessToken")).isFalse();

    }
}
