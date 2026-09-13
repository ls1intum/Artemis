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

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;

class ProviderFailureClassTest {

    @Test
    void onlyRejectionsProveNoUsageNotServerErrors() {
        var internal = InternalServerException.builder().statusCode(500).headers(Headers.builder().build()).build();
        var rateLimited = RateLimitException.builder().headers(Headers.builder().build()).build();
        var badRequest = BadRequestException.builder().headers(Headers.builder().build()).build();

        assertThat(ProviderFailureClass.of(internal)).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.of(rateLimited)).isEqualTo(ProviderFailureClass.REJECTED_TRANSIENT);
        assertThat(ProviderFailureClass.of(badRequest)).isEqualTo(ProviderFailureClass.REJECTED);
        assertThat(ProviderFailureClass.REJECTED.provesNoUsage()).isTrue();
        assertThat(ProviderFailureClass.REJECTED.retryable()).isFalse();
        // Wrapped by a client layer: the status still decides.
        assertThat(ProviderFailureClass.of(new IllegalStateException("wrapped", internal))).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.describe(new IllegalStateException("wrapped", internal))).isEqualTo("IllegalStateException <- InternalServerException HTTP 500");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 200, 301, 408, 499, 500, 502, 503, 504 })
    void unknownStatusesAndTimeoutResponsesCannotProveNoUsage(int status) {
        var error = InternalServerException.builder().statusCode(status).headers(Headers.builder().build()).build();
        var classification = ProviderFailureClass.of(error);

        assertThat(classification).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(classification.provesNoUsage()).isFalse();
        assertThat(classification.retryable()).isFalse();
    }

    @Test
    void unsentRequestsRetrySafelyWhileTimeoutsAndResetsStayIndeterminate() {
        assertThat(ProviderFailureClass.of(new OpenAIIoException("io", new ConnectException("refused")))).isEqualTo(ProviderFailureClass.NOT_SENT);
        assertThat(ProviderFailureClass.of(new OpenAIIoException("io", new HttpConnectTimeoutException("connect")))).isEqualTo(ProviderFailureClass.NOT_SENT);
        assertThat(ProviderFailureClass.NOT_SENT.provesNoUsage()).isTrue();
        assertThat(ProviderFailureClass.NOT_SENT.retryable()).isTrue();

        assertThat(ProviderFailureClass.of(new OpenAIIoException("io", new HttpTimeoutException("read")))).isEqualTo(ProviderFailureClass.INDETERMINATE);
        assertThat(ProviderFailureClass.of(new OpenAIIoException("io", new IOException("reset")))).isEqualTo(ProviderFailureClass.INDETERMINATE);
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
