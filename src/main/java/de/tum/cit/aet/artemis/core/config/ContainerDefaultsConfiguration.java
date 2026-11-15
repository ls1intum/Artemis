package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The container settings that apply to the builds of every programming exercise of an installation, unless the
 * exercise sets its own. Both are empty by default, so an installation that sets nothing behaves as before.
 * <p>
 * The variables are a list of {@code KEY=value} entries and not a map on purpose. Spring lowercases the keys of a map
 * that is bound from an operating system environment variable, which is how a Kubernetes deployment passes its
 * configuration, and a variable named {@code CI} would arrive as {@code ci}. A list keeps each entry as written.
 *
 * @param env     the variables that every build container gets, as {@code KEY=value}
 * @param network the network of the build containers of exercises that select none, which is {@code none} or one of
 *                    {@code artemis.continuous-integration.container-flags-limit.allowed-custom-networks}. Blank keeps
 *                    the default network of the container runtime.
 */
@ConfigurationProperties(prefix = "artemis.continuous-integration.container-defaults")
public record ContainerDefaultsConfiguration(@DefaultValue List<String> env, @Nullable String network) {
}
