package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL;
import static de.tum.cit.aet.artemis.core.config.Constants.REDIS;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;

/**
 * Resolves which distributed data provider backs the cluster.
 *
 * <p>
 * The abstraction started out scoped to local CI, so the backend was selected by
 * {@code artemis.continuous-integration.data-store}. Now that the same provider carries all cross-node and
 * core-to-build-agent state, the property is named {@code artemis.distributed-data.provider}. The old key is still
 * honoured as a fallback so existing deployments and the Ansible templates keep working across one release; it should be
 * removed once every environment has been switched over.
 *
 * <p>
 * Centralised here because eight conditions and configuration classes need the same answer, and having each read the
 * environment directly is how they would drift apart during the rename.
 */
public final class DistributedDataProviderResolver {

    /**
     * Current property naming the distributed data provider.
     */
    public static final String PROVIDER_PROPERTY = "artemis.distributed-data.provider";

    /**
     * Superseded property, kept working for one release.
     */
    public static final String LEGACY_PROVIDER_PROPERTY = "artemis.continuous-integration.data-store";

    /**
     * The values the provider beans are conditioned on. Anything else is a misconfiguration.
     */
    private static final List<String> SUPPORTED_PROVIDERS = List.of(HAZELCAST, REDIS, LOCAL);

    private DistributedDataProviderResolver() {
    }

    /**
     * Resolves the configured provider name, preferring the current property over the superseded one.
     *
     * @param environment the Spring environment to read from
     * @return the configured provider name, or {@link Constants#HAZELCAST} if neither property is set
     */
    public static String resolveProvider(Environment environment) {
        String provider = firstNonBlank(environment.getProperty(PROVIDER_PROPERTY), environment.getProperty(LEGACY_PROVIDER_PROPERTY));
        if (provider == null) {
            return HAZELCAST;
        }
        // Every provider bean is gated on an exact match, so an unsupported value would leave the application with no
        // provider at all and fail much later with an unrelated error. Reject it where the misconfiguration actually is.
        String resolved = SUPPORTED_PROVIDERS.stream().filter(supported -> supported.equalsIgnoreCase(provider)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported distributed data provider '" + provider + "' configured via " + PROVIDER_PROPERTY + " (or the superseded "
                        + LEGACY_PROVIDER_PROPERTY + "). Supported values are " + SUPPORTED_PROVIDERS + "."));
        return resolved;
    }

    /**
     * @param values candidate property values in order of precedence
     * @return the first value that is neither null nor blank, or {@code null} if there is none
     */
    @Nullable
    private static String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * @param environment the Spring environment to read from
     * @param candidate   the provider name to compare against, case-insensitively
     * @return true if the configured provider is {@code candidate}
     */
    public static boolean isProvider(Environment environment, String candidate) {
        return resolveProvider(environment).equalsIgnoreCase(candidate);
    }
}
