package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;

/** Shared guard that lets Hyperion fail fast while a provider outage/quota/auth failure is cooling down. */
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

    /**
     * Produces the provider key shared by the guarded Hyperion chat paths.
     *
     * @param configuredModel configured provider model, if available
     * @return the stable model key or the default fallback
     */
    static String keyForModel(@Nullable String configuredModel) {
        return configuredModel == null || configuredModel.isBlank() ? "default" : configuredModel;
    }

    /**
     * Determines whether an exhausted provider call is worth a small outer retry.
     *
     * @param error provider failure
     * @return whether another outer attempt can plausibly succeed
     */
    static boolean isRetryable(Throwable error) {
        if (error instanceof ProviderInCooldownException || isQuotaOrConfigurationFailure(error)) {
            return false;
        }
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof OpenAIServiceException serviceException) {
                return isTransientStatus(serviceException.statusCode());
            }
            if (cause instanceof OpenAIIoException || cause instanceof OpenAIRetryableException || cause instanceof IOException) {
                return true;
            }
        }
        Integer status = firstHttpStatusInMessage(error);
        return status == null || isTransientStatus(status);
    }

    /**
     * Returns a no-op implementation for focused tests that do not exercise provider admission.
     *
     * @return the shared no-op implementation
     */
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

    private static boolean isTransientStatus(int status) {
        return status == 408 || status == 409 || status == 429 || status >= 500;
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
            // Intentionally disabled.
        }
    }

}
