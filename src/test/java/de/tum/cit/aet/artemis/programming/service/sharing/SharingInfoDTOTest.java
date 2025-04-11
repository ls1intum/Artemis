package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;

public class SharingInfoDTOTest {

    @Test
    void someEqualsTests() {
        SharingInfoDTO si = new SharingInfoDTO();
        SharingInfoDTO si2 = new SharingInfoDTO();

        assertThat(si.equals(si2)).isTrue();
        assertThat(si.equals(si)).isTrue();
        assertThat(si.equals(null)).isFalse();

        si.setExercisePosition(2);
        assertThat(si.equals(si2)).isFalse();

        si2.setExercisePosition(2);
        assertThat(si.equals(si2)).isTrue();

        si.setBasketToken("someBasketToken");
        assertThat(si.equals(si2)).isFalse();

        si2.setBasketToken("someBasketToken");
        assertThat(si.equals(si2)).isTrue();

        si.setApiBaseURL("someApiBaseUrl");
        assertThat(si.equals(si2)).isFalse();

        si2.setApiBaseURL("someApiBaseUrl");
        assertThat(si.equals(si2)).isTrue();

        si.setReturnURL("SomeReturnUrl");
        assertThat(si.equals(si2)).isFalse();

        si2.setReturnURL("SomeReturnUrl");
        assertThat(si.equals(si2)).isTrue();

        si.setChecksum("Not relevant for equals");
        assertThat(si.equals(si2)).isTrue();
    }
}
