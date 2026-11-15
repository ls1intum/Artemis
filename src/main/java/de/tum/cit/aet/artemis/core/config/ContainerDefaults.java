package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;

/**
 * Holds the environment variables and the network that the build container of every programming exercise gets unless
 * the exercise says otherwise, see {@link ContainerDefaultsConfiguration}.
 * <p>
 * The values are checked once, at startup, because a wrong one would otherwise fail every build of every exercise
 * instead of one: an entry that is not {@code KEY=value} cannot be passed to a container, and a network that exercises
 * may not select would be refused when a build starts, on a host that may not have it. The error names the property
 * and the position of the entry, and never the value, because a variable can hold something that must not reach a log.
 * <p>
 * Everything the container gets is readable by the student code that runs in it, so a default is no place for a
 * credential that can do more than the build needs.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy(false)
@EnableConfigurationProperties(ContainerDefaultsConfiguration.class)
public class ContainerDefaults {

    private static final Logger log = LoggerFactory.getLogger(ContainerDefaults.class);

    private static final String PROPERTY = "artemis.continuous-integration.container-defaults";

    private static final String ALLOWED_NETWORKS_PROPERTY = "artemis.continuous-integration.container-flags-limit.allowed-custom-networks";

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Map<String, String> environment;

    @Nullable
    private final String network;

    public ContainerDefaults(ContainerDefaultsConfiguration configuration, @Value("${" + ALLOWED_NETWORKS_PROPERTY + ":none}") List<String> allowedNetworks) {
        this.environment = parseEnvironment(configuration.env());
        this.network = checkNetwork(StringUtils.trimToNull(configuration.network()), allowedNetworks);
    }

    private static Map<String, String> parseEnvironment(@Nullable List<String> entries) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (entries == null) {
            return Map.of();
        }
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            int separator = entry == null ? -1 : entry.indexOf('=');
            String name = separator < 1 ? "" : entry.substring(0, separator).trim();
            if (!VARIABLE_NAME.matcher(name).matches()) {
                throw new IllegalStateException("Entry " + index + " of " + PROPERTY
                        + ".env is not of the form KEY=value, with a name of letters, digits and underscores that does not start with a digit.");
            }
            // a later entry wins over an earlier one with the same name, like a later assignment would
            parsed.put(name, entry.substring(separator + 1));
        }
        return Map.copyOf(parsed);
    }

    @Nullable
    private static String checkNetwork(@Nullable String network, @Nullable List<String> allowedNetworks) {
        if (network != null && (allowedNetworks == null || !allowedNetworks.contains(network))) {
            throw new IllegalStateException(PROPERTY + ".network is '" + network + "', which exercises may not select. It must be 'none' or one of " + ALLOWED_NETWORKS_PROPERTY
                    + ", which is " + allowedNetworks + ", so that every build that gets it is accepted by the build agents.");
        }
        // the build agents refuse a container that shares the network of the host or of another container, even if the list above names it
        if (DockerRunConfig.sharesNetworkNamespace(network)) {
            throw new IllegalStateException(
                    PROPERTY + ".network is '" + network + "', which shares the network of the host or of another container. The build agents refuse it for every build.");
        }
        return network;
    }

    /**
     * Logs what every build container gets, by name only, so that an operator who expects a default can tell from the startup log
     * that it is active, and one who does not can tell that nothing is set.
     */
    @PostConstruct
    public void logConfiguredDefaults() {
        if (environment.isEmpty() && network == null) {
            log.info("No container defaults are configured ({}), so the container of a build only gets what its exercise sets.", PROPERTY);
            return;
        }
        log.info("Every build container gets the variables {} and the network {}, unless its exercise sets its own.", environment.keySet().stream().sorted().toList(),
                network == null ? "of the container runtime" : "'" + network + "'");
    }

    /**
     * @return the variables of every build container. An exercise that sets one of them with the same name overrides it.
     */
    public Map<String, String> environment() {
        return environment;
    }

    /**
     * @return the network of the builds of exercises that select none, or null for the default of the container runtime
     */
    @Nullable
    public String network() {
        return network;
    }
}
