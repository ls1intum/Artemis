package de.tum.cit.aet.artemis.communication.service.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LinkPreviewHttpClientResponseTest {

    private static LinkPreviewHttpClient.Response response(int status, byte[] body) {
        return new LinkPreviewHttpClient.Response(status, Map.of("x-test", List.of("a")), body);
    }

    @Test
    void equalsAndHashCodeCompareBodyByContent() {
        var response = response(200, new byte[] { 1, 2 });
        assertThat(response).isEqualTo(response);
        assertThat(response).isEqualTo(response(200, new byte[] { 1, 2 }));
        assertThat(response).hasSameHashCodeAs(response(200, new byte[] { 1, 2 }));
        assertThat(response).isNotEqualTo(response(200, new byte[] { 1, 3 }));
        assertThat(response).isNotEqualTo(response(404, new byte[] { 1, 2 }));
        assertThat(response).isNotEqualTo(new LinkPreviewHttpClient.Response(200, Map.of(), new byte[] { 1, 2 }));
        assertThat(response).isNotEqualTo(null);
        assertThat(response).isNotEqualTo("response");
    }

    @Test
    void toStringRendersBodyBytes() {
        assertThat(response(200, new byte[] { 1, 2 }).toString()).contains("statusCode=200", "x-test", "body=[1, 2]");
    }
}
