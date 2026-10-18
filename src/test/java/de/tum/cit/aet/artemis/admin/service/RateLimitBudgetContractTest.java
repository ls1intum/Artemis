package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import de.tum.cit.aet.artemis.core.config.RateLimitConfig;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import inet.ipaddr.IPAddressString;
import io.github.bucket4j.distributed.proxy.ProxyManager;

class RateLimitBudgetContractTest {

    @Test
    void budgetInspectionDoesNotSpendTokensAndDeclinesStayIsolatedByAddressAndOperation() {
        var configuration = mock(RateLimitConfigurationService.class);
        var features = mock(FeatureToggleService.class);
        when(configuration.isRateLimitingEnabled()).thenReturn(true);
        when(configuration.getEffectiveRpm(any())).thenReturn(1);
        when(features.isFeatureEnabled(Feature.RateLimit)).thenReturn(true);
        var service = new RateLimitService(new RateLimitConfig().localRateLimitProxyManager(), configuration, features);
        var client = new IPAddressString("203.0.113.1").getAddress();
        var other = new IPAddressString("203.0.113.2").getAddress();
        var type = RateLimitType.BUILD_AGENT_CLONE_TOKEN;

        assertThat(service.hasRemainingBudget(client, type)).isTrue();
        assertThat(service.hasRemainingBudget(client, type)).isTrue();
        service.consumePerMinute(client, type);
        assertThat(service.hasRemainingBudget(client, type)).isFalse();
        assertThatCode(() -> service.consumePerMinute(client, type)).doesNotThrowAnyException();
        assertThat(service.hasRemainingBudget(client, type)).isFalse();
        assertThat(service.hasRemainingBudget(other, type)).isTrue();
        assertThat(service.hasRemainingBudget(client, RateLimitType.AUTHENTICATION)).isTrue();
        service.enforcePerMinute(other, type);
        assertThat(service.hasRemainingBudget(other, type)).isFalse();
        assertThatThrownBy(() -> service.enforcePerMinute(client, type)).isInstanceOfSatisfying(RateLimitExceededException.class,
                failure -> assertThat(failure.getRetryAfterSeconds()).isBetween(1L, 60L));
    }

    @ParameterizedTest
    @CsvSource({ "false, false, false", "false, true, false", "false, true, true", "true, false, false", "true, false, true", "true, true, true" })
    @SuppressWarnings("unchecked")
    void disabledOrExemptClientsDoNotCreateOrSpendBuckets(boolean enabled, boolean feature, boolean exempt) {
        ProxyManager<String> buckets = mock(ProxyManager.class);
        var configuration = mock(RateLimitConfigurationService.class);
        var features = mock(FeatureToggleService.class);
        var client = new IPAddressString("203.0.113.1").getAddress();
        when(configuration.isRateLimitingEnabled()).thenReturn(enabled);
        when(configuration.isExempt(client)).thenReturn(exempt);
        when(features.isFeatureEnabled(Feature.RateLimit)).thenReturn(feature);
        var service = new RateLimitService(buckets, configuration, features);

        assertThat(service.hasRemainingBudget(client, RateLimitType.AUTHENTICATION)).isTrue();
        assertThatCode(() -> service.consumePerMinute(client, RateLimitType.AUTHENTICATION)).doesNotThrowAnyException();
        assertThatCode(() -> service.enforcePerMinute(client, RateLimitType.AUTHENTICATION)).doesNotThrowAnyException();
        verifyNoInteractions(buckets);
    }

    @Test
    void missingRequestContextAndMissingRequestReferenceProduceNoInventedClientIdentity() {
        var service = new RateLimitService(null, null, null);
        try (var context = mockStatic(RequestContextHolder.class)) {
            assertThat(service.resolveClientId()).isNull();
            var attributes = mock(RequestAttributes.class);
            context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
            assertThat(service.resolveClientId()).isNull();
        }
    }
}
