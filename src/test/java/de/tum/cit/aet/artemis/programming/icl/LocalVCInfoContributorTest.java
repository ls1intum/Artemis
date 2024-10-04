package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCInfoContributor;

@ActiveProfiles({ PROFILE_ARTEMIS, PROFILE_LOCALVC, PROFILE_BUILDAGENT })
class LocalVCInfoContributorTest {

    @SpyBean
    private LocalVCInfoContributor localVCInfoContributor;

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
        assertThat((Boolean) info.getDetails().get("useVersionControlAccessToken")).isFalse();
    }
}
