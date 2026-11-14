package de.tum.cit.aet.artemis.core.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;

/**
 * Decides from which hosts rendered markdown may load images.
 * <p>
 * The hosts come from the setting {@code info.allowedImageHosts}, a comma separated list of host names. An entry that starts with {@code *.} stands for every subdomain
 * of the host that follows. While the list is empty, which is the default, images may come from every host. Otherwise only images of the listed hosts and of the own
 * host of the server are kept. Relative addresses and {@code data:} addresses always stay, because they name no other host.
 *
 * @param allowedHosts     the lower-case host names, possibly with a leading {@code *.}
 * @param serverHostOrNull the lower-case host of the server itself, or {@code null} if unknown
 */
public record ImageHostPolicy(List<String> allowedHosts, @Nullable String serverHostOrNull) {

    private static final Pattern URL_SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*:.*");

    /**
     * Creates the policy from the configured list.
     *
     * @param configuredHosts the comma separated hosts of {@code info.allowedImageHosts}, may be {@code null} or blank
     * @param serverUrl       the URL of the server, whose host is always allowed
     * @return the policy
     */
    public static ImageHostPolicy of(@Nullable String configuredHosts, @Nullable String serverUrl) {
        List<String> hosts = configuredHosts == null ? List.of()
                : Arrays.stream(configuredHosts.split(",")).map(host -> host.trim().toLowerCase(Locale.ROOT)).filter(host -> !host.isEmpty()).toList();
        return new ImageHostPolicy(hosts, serverUrl == null ? null : hostOf(serverUrl));
    }

    /**
     * Whether every host is allowed, which is the case while no host is configured.
     *
     * @return true if there is no restriction
     */
    public boolean isOpen() {
        return allowedHosts.isEmpty();
    }

    /**
     * Whether an image with this address may be shown.
     *
     * @param src the value of the {@code src} attribute of the image
     * @return true if the image may be shown
     */
    public boolean allows(@Nullable String src) {
        if (isOpen() || src == null || src.isBlank()) {
            return true;
        }
        String trimmed = src.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:")) {
            return true;
        }
        boolean hasHost = lower.startsWith("//") || lower.startsWith("http://") || lower.startsWith("https://");
        if (!hasHost) {
            // A relative address stays on the page. Any other scheme would not be shown by the sanitizers in the first place.
            return !URL_SCHEME.matcher(lower).matches();
        }
        String host = hostOf(lower.startsWith("//") ? "https:" + lower : lower);
        if (host == null) {
            return false;
        }
        return host.equals(serverHostOrNull) || allowedHosts.stream().anyMatch(allowed -> matches(allowed, host));
    }

    /**
     * Removes the images of the subtree whose address is not allowed.
     *
     * @param root the element to clean
     */
    public void removeDisallowedImages(Element root) {
        if (isOpen()) {
            return;
        }
        root.select("img").forEach(image -> {
            if (!allows(image.attr("src"))) {
                image.remove();
            }
        });
    }

    private static boolean matches(String allowed, String host) {
        if (allowed.startsWith("*.")) {
            return host.endsWith(allowed.substring(1));
        }
        return allowed.equals(host);
    }

    private static @Nullable String hostOf(String url) {
        try {
            String host = new URI(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        }
        catch (URISyntaxException e) {
            return null;
        }
    }
}
