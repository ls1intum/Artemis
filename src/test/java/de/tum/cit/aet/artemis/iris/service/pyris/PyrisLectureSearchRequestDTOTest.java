package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;

class PyrisLectureSearchRequestDTOTest {

    @Test
    void preservesRawArtemisBaseUrl() {
        String artemisBaseUrl = "https://artemis.example.org/";

        var request = new PyrisLectureSearchRequestDTO("query", 5, artemisBaseUrl, null, null, null);

        assertThat(request.artemisBaseUrl()).isEqualTo(artemisBaseUrl);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t", "\n" })
    void rejectsBlankArtemisBaseUrl(String artemisBaseUrl) {
        assertThatThrownBy(() -> new PyrisLectureSearchRequestDTO("query", 5, artemisBaseUrl, null, null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artemis base URL must not be blank");
    }
}
