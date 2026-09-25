package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;

class ProviderFailureCooldownTest {

    @Test
    void expiredStoredDeadlineDoesNotBlockExecution() {
        var cooldown = new TestProviderFailureCooldown();
        cooldown.startCooldown("model", Instant.EPOCH);
        assertThat(cooldown.execute("model", Duration.ofMinutes(1), () -> "called")).isEqualTo("called");
    }

    @Test
    void futureDeadlineBlocksBeforeCallingProvider() {
        var cooldown = new TestProviderFailureCooldown();
        cooldown.startCooldown("model", Instant.now().plusSeconds(3600));
        var called = new AtomicBoolean();
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> called.getAndSet(true)))
                .isInstanceOf(ProviderFailureCooldown.ProviderInCooldownException.class);
        assertThat(called).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "billing", "model does not exist", "insufficient_quota", "deployment_not_found" })
    void serverErrorBodyCannotOpenSharedCooldown(String echoedText) {
        var cooldown = new TestProviderFailureCooldown();
        var error = new IllegalStateException(echoedText, InternalServerException.builder().statusCode(500).headers(Headers.builder().build()).build());
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> {
            throw error;
        })).isSameAs(error);
        assertThat(cooldown.cooldownUntil("model")).isNull();
    }

    @Test
    void rejectedCredentialsOpenTheSharedCooldown() {
        var cooldown = new TestProviderFailureCooldown();
        var error = new IllegalStateException("call failed", UnauthorizedException.builder().headers(Headers.builder().build()).build());
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> {
            throw error;
        })).isSameAs(error);
        assertThat(cooldown.cooldownUntil("model")).isAfter(Instant.now());
    }

    @Test
    void exhaustedQuotaOpensTheCooldownWhilePlainRateLimitingDoesNot() {
        var cooldown = new TestProviderFailureCooldown();
        var rateLimited = new IllegalStateException("slow down", RateLimitException.builder().headers(Headers.builder().build()).build());
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> {
            throw rateLimited;
        })).isSameAs(rateLimited);
        assertThat(cooldown.cooldownUntil("model")).isNull();

        var quota = new IllegalStateException("You exceeded your current quota (insufficient_quota)", RateLimitException.builder().headers(Headers.builder().build()).build());
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> {
            throw quota;
        })).isSameAs(quota);
        assertThat(cooldown.cooldownUntil("model")).isAfter(Instant.now());
    }

    @Test
    void missingModelOpensTheCooldownWhileOtherNotFoundResponsesDoNot() {
        var cooldown = new TestProviderFailureCooldown();
        var otherNotFound = new IllegalStateException("resource missing", NotFoundException.builder().headers(Headers.builder().build()).build());
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> {
            throw otherNotFound;
        })).isSameAs(otherNotFound);
        assertThat(cooldown.cooldownUntil("model")).isNull();

        var missingModel = new IllegalStateException("The model does not exist (model_not_found)", NotFoundException.builder().headers(Headers.builder().build()).build());
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> {
            throw missingModel;
        })).isSameAs(missingModel);
        assertThat(cooldown.cooldownUntil("model")).isAfter(Instant.now());
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTTP 401 Unauthorized", "status 429: insufficient_quota", "status 429: rate limit reached", "status 503: billing service unavailable",
            "insufficient_quota without any status", "The response quoted HTTP 403 from the exercise", "Transport failed after parsing error code 401" })
    void unstructuredMessagesCannotOpenASharedCooldown(String message) {
        var cooldown = new TestProviderFailureCooldown();
        var error = new IllegalStateException("transport failed", new IllegalStateException(message));
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> {
            throw error;
        })).isSameAs(error);
        assertThat(cooldown.cooldownUntil("model")).isNull();
    }
}
