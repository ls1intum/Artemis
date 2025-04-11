package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Assertions;
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

    @Test
    void testVariousOnSharingZipFile() throws IOException {
        final String testZipName = "testZip";
        try (SharingMultipartZipFile sharingZip = new SharingMultipartZipFile(testZipName, this.getClass().getResource("./basket/sampleExercise.zip").openStream());) {
            assertThat(sharingZip.getName()).isEqualTo(testZipName);
            assertThat(sharingZip.getContentType()).isEqualTo("application/zip");
            assertThat(sharingZip.getInputStream()).isNotNull();
            assertThat(sharingZip.isEmpty()).isFalse();
            assertThat(sharingZip.getSize()).isGreaterThan(0);
            File tmpFile = File.createTempFile("zipTest", "zip");
            sharingZip.transferTo(tmpFile);
            assertThat(tmpFile.length()).isGreaterThan(0);
            assertThat(sharingZip.getSize()).isEqualTo(0);
        }
    }

    @Test
    void testWeirdUseOfSharingZipFile() throws IOException {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SharingMultipartZipFile(null, this.getClass().getResource("./basket/sampleExercise.zip").openStream()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SharingMultipartZipFile("no stream", null));
    }

}
