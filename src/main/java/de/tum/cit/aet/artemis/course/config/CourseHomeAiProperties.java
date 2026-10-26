package de.tum.cit.aet.artemis.course.config;

import java.net.URI;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;

/** Explicit, independent provider configuration. Missing values leave Home deterministic. */
@Lazy
@ConfigurationProperties(prefix = "artemis.course-home.ai")
public record CourseHomeAiProperties(boolean enabled, @Nullable String location, @Nullable String baseUrl, @Nullable String apiKey, @Nullable String model) {

    public String providerLocation() {
        return location == null ? "" : location.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Checks whether all required provider settings are valid and explicitly enabled.
     *
     * @return true when provider configuration is complete and valid
     */
    public boolean isConfigured() {
        if (!enabled || !(providerLocation().equals("LOCAL") || providerLocation().equals("CLOUD")) || baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()
                || model == null || model.isBlank()) {
            return false;
        }
        try {
            var uri = URI.create(baseUrl);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        }
        catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "CourseHomeAiProperties[enabled=" + enabled + ", location=" + providerLocation() + ", configured=" + isConfigured() + "]";
    }
}
