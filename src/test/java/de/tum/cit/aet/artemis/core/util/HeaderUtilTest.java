package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class HeaderUtilTest {

    private static final String APPLICATION_NAME = "artemisApp";

    private static final String MESSAGE_HEADER = "X-" + APPLICATION_NAME + "-message";

    /**
     * Exception messages are free-form and occasionally multi-line - the example submission assessment training packs a
     * JSON array of correction errors into one. A header value containing CR/LF makes the servlet container abandon the
     * response, so the client loses the problem detail body entirely (see {@code TutorParticipationService}).
     */
    @Test
    void shouldFoldControlCharactersOfTheFailureMessageIntoTheHeader() {
        String multiLineMessage = "{\"errors\": [ {\r\n  \"reference\" : \"class:1\",\n  \"type\" : \"INCORRECT_SCORE\"\n} ]}";

        HttpHeaders headers = HeaderUtil.createFailureAlert(APPLICATION_NAME, true, "TutorParticipation", "invalid_assessment", multiLineMessage);

        String message = headers.getFirst(MESSAGE_HEADER);
        assertThat(message).isNotNull().doesNotContain("\r").doesNotContain("\n").contains("\"reference\" : \"class:1\"");
    }

    @Test
    void shouldKeepASingleLineFailureMessageUnchanged() {
        String message = "{\"errors\": []}";

        HttpHeaders headers = HeaderUtil.createFailureAlert(APPLICATION_NAME, true, "TutorParticipation", "invalid_assessment", message);

        assertThat(headers.getFirst(MESSAGE_HEADER)).isEqualTo(message);
        assertThat(headers.getFirst("X-" + APPLICATION_NAME + "-error")).isEqualTo("error.invalid_assessment");
    }
}
