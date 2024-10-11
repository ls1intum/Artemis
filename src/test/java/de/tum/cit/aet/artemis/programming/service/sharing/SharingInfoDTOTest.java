package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;

public class SharingInfoDTOTest {

    @ParameterizedTest
    @MethodSource("provideEqualsTestCases")
    void testSharingInfoDTOEquals(SharingInfoDTO dto1, SharingInfoDTO dto2, boolean expected) {
        if (expected) {
            assertThat(dto1).isEqualTo(dto2);
        }
        else {
            assertThat(dto1).isNotEqualTo(dto2);
        }
    }

    private static Stream<Arguments> provideEqualsTestCases() {
        SharingInfoDTO base = new SharingInfoDTO();
        SharingInfoDTO withPosition = new SharingInfoDTO();
        withPosition.setExercisePosition(2);
        SharingInfoDTO withBasketToken = new SharingInfoDTO();
        withBasketToken.setBasketToken("some Token");
        SharingInfoDTO withApi = new SharingInfoDTO();
        withBasketToken.setApiBaseURL("http://someURL/");

        return Stream.of(Arguments.of(base, new SharingInfoDTO(), true), Arguments.of(base, base, true), Arguments.of(base, null, false), Arguments.of(null, base, false),
                Arguments.of(base, withPosition, false), Arguments.of(base, withBasketToken, false), Arguments.of(base, withApi, false), Arguments.of(base, "otherType", false)
        // ... more test cases
        );
    }

    @Test
    void testSharingZipFilePropertiesAndFileTransfer() throws IOException {
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
    void testSharingZipFileThrowsExceptionForInvalidParameters() throws IOException {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SharingMultipartZipFile(null, this.getClass().getResource("./basket/sampleExercise.zip").openStream()));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new SharingMultipartZipFile("no stream", null));
    }

}
