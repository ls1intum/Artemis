package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GocastApprovalLinkService}.
 * <p>
 * Verifies that the approval-page URL is assembled and URL-encoded correctly for the
 * approval flow described in the integration spec (§5 binding flow, §7 approval-link bullet),
 * and that the service fails fast when its required configuration is missing.
 */
class GocastApprovalLinkServiceTest {

    private static final String WEB_BASE_URL = "https://tum.live";

    private static final String SERVICE_USER_ID = "42";

    private static final long GOCAST_COURSE_ID = 99L;

    private GocastApprovalLinkService service(String webBaseUrl) {
        return new GocastApprovalLinkService(webBaseUrl, SERVICE_USER_ID);
    }

    // ── happy-path: simple callback URL ───────────────────────────────────────

    @Test
    void buildApprovalLink_encodesRedirectWithSpecialChars() {
        // redirect URL contains '?', '&', and a space — must all be percent-encoded inside the query value
        String artemisCallback = "https://artemis.tum.de/cb?courseId=5&x=a b";

        String result = service(WEB_BASE_URL).buildApprovalLink(GOCAST_COURSE_ID, artemisCallback);

        // the redirect value must be encoded so the outer URL remains well-formed
        assertThat(result).contains("redirect=");
        // '&' inside the callback must be encoded (as %26), not left bare (which would break the outer URL)
        assertThat(result).doesNotContain("redirect=https://artemis.tum.de/cb?courseId=5&x=a b");
        // space must be encoded
        assertThat(result).doesNotContain(" ");

        String encodedRedirectValue = result.substring(result.indexOf("redirect=") + "redirect=".length());
        // negative: no bare special chars leak into the outer query string
        assertThat(encodedRedirectValue).doesNotContain("?");
        assertThat(encodedRedirectValue).doesNotContain("&");
        assertThat(encodedRedirectValue).doesNotContain(" ");
        // positive: the expected single-level percent-encoded forms are present (guards against
        // double-encoding, which would produce %253F / %2526 / %2520 instead of %3F / %26 / %20)
        assertThat(encodedRedirectValue).contains("%3F"); // '?'
        assertThat(encodedRedirectValue).contains("%26"); // '&'
        assertThat(encodedRedirectValue).contains("%20"); // space
        assertThat(encodedRedirectValue).doesNotContain("%25"); // no double-encoded '%'
    }

    @Test
    void buildApprovalLink_toleratesTrailingSlashOnBaseUrl() {
        String artemisCallback = "https://artemis.tum.de/callback";

        String result = service(WEB_BASE_URL + "/").buildApprovalLink(GOCAST_COURSE_ID, artemisCallback);

        // must not produce double slash before "admin"
        assertThat(result).doesNotContain("//admin");
        assertThat(result).contains("/admin/course/" + GOCAST_COURSE_ID + "/integration/confirm");
    }

    @Test
    void buildApprovalLink_exactUrlForSimpleCallback() {
        String artemisCallback = "https://artemis.tum.de/callback";

        String result = service(WEB_BASE_URL).buildApprovalLink(GOCAST_COURSE_ID, artemisCallback);

        // The redirect value must be percent-encoded (the colon in "https:" → "%3A", slashes → "%2F")
        assertThat(result).isEqualTo("https://tum.live/admin/course/99/integration/confirm?service=42&redirect=https%3A%2F%2Fartemis.tum.de%2Fcallback");
    }

    @Test
    void buildApprovalLink_throwsOnNullCallbackUrl() {
        assertThatThrownBy(() -> service(WEB_BASE_URL).buildApprovalLink(GOCAST_COURSE_ID, null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("artemisCallbackUrl");
    }

    // ── fail-fast on missing required configuration ───────────────────────────

    @Test
    void construction_throwsWhenWebBaseUrlBlank() {
        assertThatThrownBy(() -> new GocastApprovalLinkService("", SERVICE_USER_ID)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artemis.tum-live.web-base-url");
    }

    @Test
    void construction_throwsWhenWebBaseUrlWhitespaceOnly() {
        assertThatThrownBy(() -> new GocastApprovalLinkService("   ", SERVICE_USER_ID)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artemis.tum-live.web-base-url");
    }

    @Test
    void construction_throwsWhenServiceUserIdBlank() {
        assertThatThrownBy(() -> new GocastApprovalLinkService(WEB_BASE_URL, "")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artemis.tum-live.service-account-user-id");
    }
}
