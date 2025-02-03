package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.dto.LinkPreviewDTO;
import de.tum.cit.aet.artemis.communication.service.linkpreview.LinkPreviewService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

/**
 * REST controller for Link Preview.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class LinkPreviewResource {

    private static final Logger log = LoggerFactory.getLogger(LinkPreviewResource.class);

    private final LinkPreviewService linkPreviewService;

    // Regular expression to match valid domain names with a TLD
    private static final Pattern VALID_DOMAIN_PATTERN = Pattern.compile("^(?!-)([a-zA-Z0-9-]{1,63}\\.)+[a-zA-Z]{2,20}$");

    public LinkPreviewResource(LinkPreviewService linkPreviewService) {
        this.linkPreviewService = linkPreviewService;
    }

    /**
     * GET /link-preview : Fetches link preview for the given URL.
     *
     * <p>
     * This endpoint validates the input URL and returns metadata (title, description, image).
     * It ensures that only public internet URLs with a valid domain and TLD are allowed.
     * </p>
     *
     * <p>
     * <b>Validation Rules:</b>
     * </p>
     * <ul>
     * <li><b>Allowed:</b> Public internet domains with a valid TLD (e.g., example.com, news.site.org).</li>
     * <li><b>Blocked:</b></li>
     * <ul>
     * <li>All IPv4 and IPv6 addresses (e.g., 192.168.1.1, 10.0.0.1, fe80::1, ::1).</li>
     * <li>Local and private network domains (e.g., localhost, intranet.local).</li>
     * <li>Non-HTTP/HTTPS URLs (e.g., ftp://, file://, javascript:).</li>
     * <li>Invalid or malformed URLs.</li>
     * </ul>
     * </ul>
     *
     * @param url The publicly accessible URL to generate a preview for.
     * @return A {@link LinkPreviewDTO} containing metadata extracted from the URL.
     * @throws BadRequestAlertException If the URL is invalid or deemed unsafe.
     */
    @GetMapping("link-preview")
    @EnforceAtLeastStudent
    @Cacheable(value = "linkPreview", key = "#url", unless = "#result == null")
    public LinkPreviewDTO getLinkPreview(@RequestParam String url) {
        log.debug("REST request to get link preview for URL: {}", url);

        if (!isValidUrl(url)) {
            throw new BadRequestAlertException("Invalid or potentially unsafe URL", "url", "invalid");
        }

        return linkPreviewService.getLinkPreview(url);
    }

    /**
     * Validates if the given URL is a public internet URL with a proper domain and TLD.
     *
     * @param url The URL string to validate.
     * @return {@code true} if the URL is valid and safe, {@code false} otherwise.
     */
    private boolean isValidUrl(String url) {
        try {
            var parsedUrl = URI.create(url).toURL();
            String protocol = parsedUrl.getProtocol();

            // Allow only HTTP and HTTPS
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return false;
            }

            String host = parsedUrl.getHost();

            // Reject if the host is an IP address (IPv4 or IPv6)
            if (isIpAddress(host)) {
                return false;
            }

            // Specifically block localhost/private IPs to prevent SSRF (Server-Side Request Forgery)
            if (Pattern.matches("^(localhost|127\\.0\\.0\\.1|::1|0\\.0\\.0\\.0|192\\.168\\..*|10\\..*|172\\.(1[6-9]|2[0-9]|3[0-1])\\..*)$", host)) {
                return false;
            }

            // Ensure the host is a valid domain with a recognized TLD
            return VALID_DOMAIN_PATTERN.matcher(host).matches();
        }
        catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Checks if the given host is an IPv4 or IPv6 address.
     *
     * @param host The hostname or IP to check.
     * @return {@code true} if it's an IP address, {@code false} otherwise.
     */
    private boolean isIpAddress(String host) {
        return Pattern.matches("^(\\d{1,3}\\.){3}\\d{1,3}$|^([a-fA-F0-9:]+:+)+[a-fA-F0-9]+$", host);
    }
}
