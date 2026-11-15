package de.tum.cit.aet.artemis.core.config;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * The registries that the Docker image of a programming exercise may come from, configured with
 * {@code artemis.continuous-integration.allowed-docker-registries}. An empty list allows every registry.
 * <p>
 * The property is bound with Spring's {@link Binder}, so that both the YAML list syntax and a comma separated string work.
 * A plain {@code @Value} injection does not see a YAML list, because the property sources store its entries as {@code name[0]}, {@code name[1]}, and so on.
 */
public final class AllowedDockerRegistries {

    private static final Logger log = LoggerFactory.getLogger(AllowedDockerRegistries.class);

    /** The name of the property that holds the list. */
    public static final String PROPERTY = "artemis.continuous-integration.allowed-docker-registries";

    private static final String DEFAULT_REGISTRY = "docker.io";

    private final List<String> registries;

    private AllowedDockerRegistries(List<String> registries) {
        this.registries = registries;
    }

    /**
     * Reads the list from the property sources of the environment.
     *
     * @param environment the environment to read {@value #PROPERTY} from
     * @return the allow-list, which allows every registry when the property is missing or empty
     */
    public static AllowedDockerRegistries from(Environment environment) {
        List<String> configured = Binder.get(environment).bind(PROPERTY, Bindable.listOf(String.class)).orElse(List.of());
        return new AllowedDockerRegistries(configured.stream().map(AllowedDockerRegistries::normalizeEntry).filter(entry -> !entry.isEmpty()).distinct().toList());
    }

    /**
     * Logs the active list once, so that an operator can see at startup whether the check is on.
     */
    public void logActiveList() {
        if (registries.isEmpty()) {
            log.info("Docker images of programming exercises may come from any registry ({} is empty).", PROPERTY);
        }
        else {
            log.info("Docker images of programming exercises may only come from the registries {} ({}).", registries, PROPERTY);
        }
    }

    /**
     * Whether the list allows every registry.
     *
     * @return true if no registry is configured
     */
    public boolean isEmpty() {
        return registries.isEmpty();
    }

    /**
     * Whether the registry of the image is on the list.
     *
     * @param dockerImage the image reference as an instructor enters it
     * @return true if the list is empty or contains the registry of the image
     */
    public boolean allows(String dockerImage) {
        return registries.isEmpty() || registries.contains(registryOf(dockerImage));
    }

    /**
     * Determines the registry of an image reference like Docker does. The part before the first slash is a registry host if it contains a dot or a colon, equals
     * {@code localhost} or contains an uppercase letter, because a repository name may not contain one. Every other image comes from Docker Hub, {@code docker.io}.
     *
     * @param dockerImage the image reference
     * @return the registry in lower case, with the alias hosts of Docker Hub mapped to {@code docker.io}
     */
    public static String registryOf(String dockerImage) {
        String image = dockerImage.trim();
        int firstSlash = image.indexOf('/');
        if (firstSlash <= 0) {
            return DEFAULT_REGISTRY;
        }
        String first = image.substring(0, firstSlash);
        boolean isHost = first.contains(".") || first.contains(":") || "localhost".equals(first) || !first.equals(first.toLowerCase(Locale.ROOT));
        return isHost ? normalizeEntry(first) : DEFAULT_REGISTRY;
    }

    /**
     * Brings a registry host to the form that {@link #registryOf} returns: lower case, without a scheme and trailing slashes, and with the alias hosts of Docker Hub
     * replaced by {@code docker.io}.
     */
    private static String normalizeEntry(String entry) {
        String normalized = entry.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        }
        else if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return "index.docker.io".equals(normalized) || "registry-1.docker.io".equals(normalized) ? DEFAULT_REGISTRY : normalized;
    }
}
