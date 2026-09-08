package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.openai.errors.OpenAIServiceException;

/**
 * Shared guard that lets Hyperion fail fast while a provider failure that no retry can fix is cooling down: exhausted quota or billing, a missing model or deployment, and
 * rejected credentials (401/403). Rate limits (429) and provider-side 5xx do NOT open a cooldown — they are transient, the SDK client already retries them, and pausing every
 * node on one of them would take the feature down for a blip.
 */
public interface ProviderFailureCooldown {

    Pattern HTTP_STATUS_IN_MESSAGE = Pattern.compile("(?i)(?:http|status|code|error)\\D{0,6}([1-5]\\d{2})\\b");

    @Nullable
    Instant cooldownUntil(String key);

    void startCooldown(String key, Instant until);

    /**
     * Admits one provider call and records hard provider failures through the same shared classification seam.
     *
     * @param <T>                 provider response type
     * @param key                 shared provider/model key
     * @param hardFailureCooldown cooldown duration after a classified hard failure
     * @param providerCall        provider request to admit
     * @return the provider response
     * @throws ProviderInCooldownException without invoking {@code providerCall} while another node's hard-failure cooldown is active
     */
    default <T> T execute(String key, Duration hardFailureCooldown, Supplier<T> providerCall) {
        Instant until = cooldownUntil(key);
        if (until != null) {
            throw new ProviderInCooldownException(until);
        }
        try {
            return providerCall.get();
        }
        catch (RuntimeException error) {
            if (opensCooldown(error) && hardFailureCooldown != null && hardFailureCooldown.isPositive()) {
                startCooldown(key, Instant.now().plus(hardFailureCooldown));
            }
            throw error;
        }
    }

    static String keyForModel(@Nullable String configuredModel) {
        return configuredModel == null || configuredModel.isBlank() ? "default" : configuredModel;
    }

    static ProviderFailureCooldown disabled() {
        return Disabled.INSTANCE;
    }

    private static boolean opensCooldown(Throwable error) {
        Integer status = firstOpenAiStatus(error);
        if (status == null) {
            status = firstHttpStatusInMessage(error);
        }
        return isQuotaOrConfigurationFailure(error) || isProviderConfigurationFailure(error) || status != null && (status == 401 || status == 403);
    }

    @Nullable
    private static Integer firstOpenAiStatus(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof OpenAIServiceException serviceException) {
                return serviceException.statusCode();
            }
        }
        return null;
    }

    private static boolean isQuotaOrConfigurationFailure(Throwable error) {
        String message = messages(error);
        return message.contains("insufficient_quota") || message.contains("exceeded your current quota") || message.contains("billing") || message.contains("hard limit")
                || message.contains("monthly limit");
    }

    private static boolean isProviderConfigurationFailure(Throwable error) {
        String message = messages(error);
        return message.contains("model_not_found") || message.contains("deployment_not_found") || message.contains("deployment not found")
                || message.contains("model does not exist") || message.contains("no model") && message.contains("found");
    }

    private static String messages(Throwable error) {
        StringBuilder messages = new StringBuilder();
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                messages.append(cause.getMessage()).append('\n');
            }
        }
        return messages.toString().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static Integer firstHttpStatusInMessage(Throwable error) {
        Matcher matcher = HTTP_STATUS_IN_MESSAGE.matcher(messages(error));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** Raised before a provider request when a shared hard-failure cooldown is active. */
    final class ProviderInCooldownException extends IllegalStateException {

        ProviderInCooldownException(Instant until) {
            super("The AI provider is in cooldown until " + until + ".");
        }
    }

    enum Disabled implements ProviderFailureCooldown {

        INSTANCE;

        @Nullable
        @Override
        public Instant cooldownUntil(String key) {
            return null;
        }

        @Override
        public void startCooldown(String key, Instant until) {
        }
    }

}
