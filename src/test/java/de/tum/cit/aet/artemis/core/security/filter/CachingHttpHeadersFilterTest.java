package de.tum.cit.aet.artemis.core.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;

/**
 * The lifetime this filter advertises had never been asserted, and it was wrong for years: the value was computed in
 * milliseconds and served in a header defined in seconds, so a configured seven days went out as
 * {@code max-age=604800000} — a little over nineteen years. The {@code Expires} header beside it was correct, and
 * {@code Cache-Control} wins where they disagree, so nothing looked amiss from the outside.
 */
class CachingHttpHeadersFilterTest {

    @Test
    void advertisesTheConfiguredLifetimeInSeconds() throws Exception {
        MockHttpServletResponse response = doFilterWithLifetimeOf(30);

        // Seconds, not milliseconds: 30 days is 2,592,000 seconds. The same number in milliseconds would claim 82 years.
        assertThat(response.getHeader("Cache-Control")).isEqualTo("max-age=2592000, public");
    }

    @Test
    void expiresAgreesWithMaxAge() throws Exception {
        // The two headers describe the same lifetime in different units, and a browser that trusts either must reach
        // the same conclusion. They disagreed by four orders of magnitude before this was fixed.
        MockHttpServletResponse response = doFilterWithLifetimeOf(30);

        long maxAgeSeconds = Long.parseLong(response.getHeader("Cache-Control").replaceAll("\\D+", ""));
        Instant expires = Instant.ofEpochMilli(response.getDateHeader("Expires"));

        assertThat(expires).isCloseTo(Instant.now().plusSeconds(maxAgeSeconds), within(Duration.ofMinutes(1)));
    }

    @Test
    void aDifferentConfiguredLifetimeIsHonoured() throws Exception {
        MockHttpServletResponse response = doFilterWithLifetimeOf(1);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("max-age=" + TimeUnit.DAYS.toSeconds(1) + ", public");
    }

    @Test
    void aResponseIsStillPassedDownTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        doFilterWithLifetimeOf(30, chain);

        assertThat(chain.getRequest()).as("the filter must not swallow the request").isNotNull();
    }

    /**
     * {@code theme-dark.css} has no content hash in its name, and the production reverse proxy caches by path and
     * ignores the hourly query the client appends. With a 30 day lifetime it kept serving the stylesheet of an older
     * release after an update, so users saw old dark mode colours (a light message hover, an empty primary button).
     */
    @ParameterizedTest
    @ValueSource(strings = { "/theme-dark.css", "/artemis/theme-dark.css", "/theme-light.css" })
    void unversionedThemeStylesheetsAreRevalidatedInsteadOfCached(String path) throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = doFilterWithLifetimeOf(30, chain, path);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isNull();
        assertThat(chain.getRequest()).as("the filter must not swallow the request").isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/styles-7WCKMG4K.css", "/main-KLCX6SYX.js", "/i18n/en.json", "/some/theme-dark.css.map" })
    void contentVersionedFilesKeepTheLongLifetime(String path) throws Exception {
        MockHttpServletResponse response = doFilterWithLifetimeOf(30, new MockFilterChain(), path);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("max-age=2592000, public");
    }

    private static MockHttpServletResponse doFilterWithLifetimeOf(int days) throws Exception {
        return doFilterWithLifetimeOf(days, new MockFilterChain());
    }

    private static MockHttpServletResponse doFilterWithLifetimeOf(int days, FilterChain chain) throws Exception {
        return doFilterWithLifetimeOf(days, chain, "/main-Z6DX4AB6.js");
    }

    private static MockHttpServletResponse doFilterWithLifetimeOf(int days, FilterChain chain, String path) throws Exception {
        ArtemisProperties properties = new ArtemisProperties();
        properties.getHttp().getCache().setVersionedAssetsTimeToLiveInDays(days);

        CachingHttpHeadersFilter filter = new CachingHttpHeadersFilter(properties);
        filter.init(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), response, chain);
        return response;
    }
}
