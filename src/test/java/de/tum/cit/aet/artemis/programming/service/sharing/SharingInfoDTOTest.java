package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;

class SharingInfoDTOTest {

    @ParameterizedTest
    @MethodSource("provideEqualsTestCases")
    void testSharingInfoDTOEquals(SharingInfoDTO dto1, Object dto2, boolean expected) {
        if (expected) {
            assertThat(dto1).isEqualTo(dto2);
        }
        else {
            assertThat(dto1).isNotEqualTo(dto2);
        }
    }

    private static Stream<Arguments> provideEqualsTestCases() {
        SharingInfoDTO base = new SharingInfoDTO(null, null, null, null, 0);
        SharingInfoDTO withPosition = new SharingInfoDTO(null, null, null, null, 2);
        SharingInfoDTO withBasketToken = new SharingInfoDTO("some Token", null, null, null, 0);
        SharingInfoDTO withApi = new SharingInfoDTO(null, null, "http://someURL/", null, 0);

        return Stream.of(Arguments.of(base, base, true), Arguments.of(base, null, false), Arguments.of(null, base, false), Arguments.of(base, withPosition, false),
                Arguments.of(base, withBasketToken, false), Arguments.of(base, withApi, false), Arguments.of(base, "otherType", false)
        // ... more test cases
        );
    }

    @Test
    void testSharingZipFilePropertiesAndFileTransfer() throws IOException {
        final String testZipName = "testZip";
        try (SharingMultipartZipFile sharingZip = new SharingMultipartZipFile(testZipName, this.getClass().getResource("./basket/sampleExercise.zip").openStream())) {
            assertThat(sharingZip.getName()).isEqualTo(testZipName);
            assertThat(sharingZip.getContentType()).isEqualTo("application/zip");
            assertThat(sharingZip.getInputStream()).isNotNull();
            assertThat(sharingZip.isEmpty()).isFalse();
            assertThat(sharingZip.getSize()).isGreaterThan(0);
            File tmpFile = File.createTempFile("zipTest", "zip");
            sharingZip.transferTo(tmpFile);
            assertThat(tmpFile.length()).isGreaterThan(0);
            assertThat(sharingZip.getSize()).isZero();
        }
    }
}
