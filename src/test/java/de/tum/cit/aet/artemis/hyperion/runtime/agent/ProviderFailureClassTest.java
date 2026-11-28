package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

class ProviderFailureClassTest {

    @Test
    void onlyRejectionsProveNoUsageNotServerErrors() {
        var internal = new HttpServerErrorException(HttpStatus.valueOf(500));
        var rateLimited = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        var badRequest = new HttpClientErrorException(HttpStatus.BAD_REQUEST);

        assertThat(ProviderFailureClass.of(internal)).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.of(rateLimited)).isEqualTo(ProviderFailureClass.REJECTED_TRANSIENT);
        assertThat(ProviderFailureClass.of(badRequest)).isEqualTo(ProviderFailureClass.REJECTED);
        assertThat(ProviderFailureClass.REJECTED.provesNoUsage()).isTrue();
        assertThat(ProviderFailureClass.REJECTED.retryable()).isFalse();
        // Wrapped by a client layer: the status still decides.
        assertThat(ProviderFailureClass.of(new IllegalStateException("wrapped", internal))).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.describe(new IllegalStateException("wrapped", internal))).isEqualTo("IllegalStateException <- HttpServerErrorException HTTP 500");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 200, 301, 408, 499, 500, 502, 503, 504 })
    void missingUnknownAndTimeoutStatusesCannotProveNoUsage(int status) {
        var error = status == 0 ? new RestClientException("No HTTP response")
                : new RestClientResponseException("Provider failed", HttpStatusCode.valueOf(status), "", null, null, null);
        var classification = ProviderFailureClass.of(error);

        assertThat(classification).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(classification.provesNoUsage()).isFalse();
        assertThat(classification.retryable()).isFalse();
    }

    @Test
    void unsentRequestsRetrySafelyWhileTimeoutsAndResetsStayIndeterminate() {
        assertThat(ProviderFailureClass.of(new ResourceAccessException("io", new ConnectException("refused")))).isEqualTo(ProviderFailureClass.NOT_SENT);
        assertThat(ProviderFailureClass.of(new ResourceAccessException("io", new HttpConnectTimeoutException("connect")))).isEqualTo(ProviderFailureClass.NOT_SENT);
        assertThat(ProviderFailureClass.NOT_SENT.provesNoUsage()).isTrue();
        assertThat(ProviderFailureClass.NOT_SENT.retryable()).isTrue();

        assertThat(ProviderFailureClass.of(new ResourceAccessException("io", new HttpTimeoutException("read")))).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.of(new ResourceAccessException("io", new IOException("reset")))).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.of(new IllegalStateException("provider unavailable"))).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.INDETERMINATE.provesNoUsage()).isFalse();
        assertThat(ProviderFailureClass.INDETERMINATE.retryable()).isFalse();
    }

    @Test
    void cooldownRejectionIsNeitherUsageNorRetryable() {
        var inCooldown = new ProviderFailureCooldown.ProviderInCooldownException(Instant.now().plusSeconds(60));
        assertThat(ProviderFailureClass.of(inCooldown)).isEqualTo(ProviderFailureClass.REJECTED);
    }
}
