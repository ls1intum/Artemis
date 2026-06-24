package de.tum.cit.aet.artemis.videosource.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Unit tests for {@link GocastEnabled} and the regression guard for {@link TumLiveEnabled}.
 * <p>
 * {@link GocastEnabled} requires BOTH {@code artemis.tum-live.api-base-url} AND
 * {@code artemis.tum-live.service-account-token} to be non-blank.
 * <p>
 * {@link TumLiveEnabled} (the existing public resolver) must NOT require the service-account token
 * — it must activate on {@code api-base-url} alone so that public TUM Live streams keep working
 * without service-account credentials.
 */
@ExtendWith(MockitoExtension.class)
class GocastEnabledTest {

    @Mock
    private ConditionContext conditionContext;

    @Mock
    private AnnotatedTypeMetadata metadata;

    // -----------------------------------------------------------------------
    // GocastEnabled — AND condition: both api-base-url AND service-account-token
    // -----------------------------------------------------------------------

    @Test
    void gocastEnabled_bothNonBlank_returnsTrue() {
        setupEnvironment("https://tum.live/api/v2", "my-secret-token", null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isTrue();
    }

    @Test
    void gocastEnabled_urlMissing_returnsFalse() {
        setupEnvironment(null, "my-secret-token", null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    @Test
    void gocastEnabled_urlBlank_returnsFalse() {
        setupEnvironment("", "my-secret-token", null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    @Test
    void gocastEnabled_urlWhitespaceOnly_returnsFalse() {
        setupEnvironment("   ", "my-secret-token", null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    @Test
    void gocastEnabled_tokenMissing_returnsFalse() {
        setupEnvironment("https://tum.live/api/v2", null, null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    @Test
    void gocastEnabled_tokenBlank_returnsFalse() {
        setupEnvironment("https://tum.live/api/v2", "", null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    @Test
    void gocastEnabled_tokenWhitespaceOnly_returnsFalse() {
        setupEnvironment("https://tum.live/api/v2", "   ", null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    @Test
    void gocastEnabled_bothMissing_returnsFalse() {
        setupEnvironment(null, null, null);

        GocastEnabled condition = new GocastEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    // -----------------------------------------------------------------------
    // TumLiveEnabled regression guard — must NOT require service-account-token
    // -----------------------------------------------------------------------

    @Test
    void tumLiveEnabled_urlOnly_returnsTrue_noTokenRequired() {
        // The public resolver must activate on api-base-url alone.
        // If a service-account-token is NOT set, TumLiveEnabled must still be true.
        setupEnvironment("https://tum.live/api/v2", null, null);

        TumLiveEnabled condition = new TumLiveEnabled();
        assertThat(condition.matches(conditionContext, metadata)).as("TumLiveEnabled must not regress: it should activate on api-base-url alone (no token required)").isTrue();
    }

    @Test
    void tumLiveEnabled_urlMissing_returnsFalse() {
        setupEnvironment(null, null, null);

        TumLiveEnabled condition = new TumLiveEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    @Test
    void tumLiveEnabled_urlBlank_returnsFalse() {
        setupEnvironment("", null, null);

        TumLiveEnabled condition = new TumLiveEnabled();
        assertThat(condition.matches(conditionContext, metadata)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Configures the mock {@link ConditionContext} to return an {@link Environment} where the given
     * properties are set. Uses {@code lenient} stubbings so that tests for conditions that only
     * inspect a subset of the properties (e.g. {@link TumLiveEnabled} ignoring the token) do not
     * trigger Mockito's {@code UnnecessaryStubbingException}.
     *
     * @param apiBaseUrl          value for {@code artemis.tum-live.api-base-url}
     * @param serviceAccountToken value for {@code artemis.tum-live.service-account-token}
     * @param webBaseUrl          value for {@code artemis.tum-live.web-base-url} (may be {@code null})
     */
    private void setupEnvironment(String apiBaseUrl, String serviceAccountToken, String webBaseUrl) {
        Environment env = mock(Environment.class);
        when(conditionContext.getEnvironment()).thenReturn(env);
        // Use lenient() for properties that may not be consumed by every Condition under test.
        lenient().when(env.getProperty("artemis.tum-live.api-base-url")).thenReturn(apiBaseUrl);
        lenient().when(env.getProperty("artemis.tum-live.service-account-token")).thenReturn(serviceAccountToken);
        if (webBaseUrl != null) {
            lenient().when(env.getProperty("artemis.tum-live.web-base-url")).thenReturn(webBaseUrl);
        }
    }
}
