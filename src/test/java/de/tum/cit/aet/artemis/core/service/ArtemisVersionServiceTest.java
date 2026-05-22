package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link ArtemisVersionService#isNewerVersionAvailable(String, String)}.
 * <p>
 * Verifies that the version comparison accepts both the new two-part canonical scheme
 * ({@code "9.2"}) and the legacy three-part hotfix scheme ({@code "10.4.1"}), and that
 * malformed inputs fall back to "no update available" instead of producing a false positive.
 */
class ArtemisVersionServiceTest {

    private ArtemisVersionService service;

    private Method isNewerVersionAvailable;

    @BeforeEach
    void setUp() throws Exception {
        service = new ArtemisVersionService(mock(RestTemplate.class), mock(CacheManager.class));
        isNewerVersionAvailable = ArtemisVersionService.class.getDeclaredMethod("isNewerVersionAvailable", String.class, String.class);
        isNewerVersionAvailable.setAccessible(true);
    }

    private boolean call(String current, String latest) throws Exception {
        return (boolean) isNewerVersionAvailable.invoke(service, current, latest);
    }

    @Test
    void detectsUpdateWhenLatestMinorIsHigherTwoPart() throws Exception {
        assertThat(call("9.2", "9.3")).isTrue();
    }

    @Test
    void noUpdateWhenVersionsMatchTwoPart() throws Exception {
        assertThat(call("9.2", "9.2")).isFalse();
    }

    @Test
    void detectsUpdateAcrossMajor() throws Exception {
        assertThat(call("9.2", "10.0")).isTrue();
    }

    @Test
    void detectsUpdateFromHotfixToNextMinor() throws Exception {
        assertThat(call("10.4.1", "10.5")).isTrue();
    }

    @Test
    void fallsBackToNoUpdateOnMalformedLatest() throws Exception {
        assertThat(call("9.2", "garbage")).isFalse();
    }

    @Test
    void stripsLeadingVPrefixInCurrent() throws Exception {
        assertThat(call("v9.2", "9.3")).isTrue();
    }
}
