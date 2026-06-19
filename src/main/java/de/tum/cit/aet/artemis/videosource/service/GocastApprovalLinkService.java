package de.tum.cit.aet.artemis.videosource.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;

/**
 * Builds the gocast approval-page URL that an instructor must visit to grant the Artemis service
 * account course-admin access.
 * <p>
 * The URL shape is:
 *
 * <pre>
 * {@code <web-base-url>/admin/course/{gocastCourseId}/integration/confirm?service={serviceUserId}&redirect={urlEncodedArtemisCallback}}
 * </pre>
 * <p>
 * The {@code redirect} query parameter contains the Artemis callback URL that gocast will redirect
 * the instructor to after the approval is confirmed. It is always fully percent-encoded (using
 * {@link URLEncoder}) so that any special characters in the callback URL — including {@code ?},
 * {@code &}, {@code :}, {@code /}, and spaces — do not break the outer approval-page URL.
 * <p>
 * This bean is only created when the gocast integration is fully configured; see {@link GocastEnabled}.
 */
@Service
@Lazy
@Conditional(GocastEnabled.class)
public class GocastApprovalLinkService {

    private final String webBaseUrl;

    private final String serviceUserId;

    /**
     * Constructs a {@link GocastApprovalLinkService} with the configured gocast web base URL and
     * service-account user ID.
     *
     * @param webBaseUrl    the base URL of the gocast web application (e.g. {@code https://tum.live});
     *                          a trailing slash is tolerated and stripped automatically
     * @param serviceUserId the gocast numeric user ID of the Artemis service account
     */
    public GocastApprovalLinkService(@Value("${artemis.tum-live.web-base-url}") String webBaseUrl, @Value("${artemis.tum-live.service-account-user-id}") String serviceUserId) {
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
        this.serviceUserId = serviceUserId;
    }

    /**
     * Builds the gocast approval-page URL for the given course.
     * <p>
     * The {@code redirect} parameter is fully percent-encoded via {@link URLEncoder} (with {@code +}
     * replaced by {@code %20} for RFC 3986 compliance), so all characters in the Artemis callback URL
     * — including {@code ?}, {@code &}, {@code :}, {@code /}, and spaces — are encoded and do not
     * break the outer URL structure.
     *
     * @param gocastCourseId     the numeric gocast course ID for which the binding is being approved
     * @param artemisCallbackUrl the full Artemis callback URL to redirect the instructor to after approval
     * @return the fully-formed, correctly encoded approval-page URL
     */
    public String buildApprovalLink(long gocastCourseId, String artemisCallbackUrl) {
        // URLEncoder encodes everything including '?', '&', ':', '/' — appropriate for an opaque value
        // embedded inside a query string. URLEncoder uses '+' for spaces; replace with '%20' (RFC 3986).
        String encodedRedirect = URLEncoder.encode(artemisCallbackUrl, StandardCharsets.UTF_8).replace("+", "%20");
        return UriComponentsBuilder.fromUriString(webBaseUrl).pathSegment("admin", "course", String.valueOf(gocastCourseId), "integration", "confirm")
                .queryParam("service", serviceUserId).queryParam("redirect", encodedRedirect).build().toUriString();
    }
}
