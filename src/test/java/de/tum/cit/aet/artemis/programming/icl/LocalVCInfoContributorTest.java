package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCInfoContributor;

class LocalVCInfoContributorTest {

    @Test
    void testContribute() {
        Info.Builder builder = new Info.Builder();
        LocalVCInfoContributor localVCInfoContributor = new LocalVCInfoContributor();
        try {
            localVCInfoContributor.contribute(builder);
        }
        catch (NullPointerException e) {
        }

        Info info = builder.build();
        assertThat((Boolean) info.getDetails().get("useVersionControlAccessToken")).isFalse();
    }
}
