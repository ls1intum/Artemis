package de.tum.cit.aet.artemis.core.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
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

    private static MockHttpServletResponse doFilterWithLifetimeOf(int days) throws Exception {
        return doFilterWithLifetimeOf(days, new MockFilterChain());
    }

    private static MockHttpServletResponse doFilterWithLifetimeOf(int days, FilterChain chain) throws Exception {
        ArtemisProperties properties = new ArtemisProperties();
        properties.getHttp().getCache().setVersionedAssetsTimeToLiveInDays(days);

        CachingHttpHeadersFilter filter = new CachingHttpHeadersFilter(properties);
        filter.init(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/main-Z6DX4AB6.js"), response, chain);
        return response;
    }
}
