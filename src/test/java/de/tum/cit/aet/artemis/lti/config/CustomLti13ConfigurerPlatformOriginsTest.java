package de.tum.cit.aet.artemis.lti.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.test_repository.LtiPlatformConfigurationTestRepository;

class CustomLti13ConfigurerPlatformOriginsTest {

    private LtiPlatformConfigurationTestRepository repository;

    private CustomLti13Configurer configurer;

    @BeforeEach
    void setUp() {
        repository = mock(LtiPlatformConfigurationTestRepository.class);
        configurer = new CustomLti13Configurer(mock(DistributedStateAuthorizationRequestRepository.class), repository);
    }

    private static LtiPlatformConfiguration platform(String originalUrl, String authorizationUri) {
        var platform = new LtiPlatformConfiguration();
        platform.setOriginalUrl(originalUrl);
        platform.setAuthorizationUri(authorizationUri);
        return platform;
    }

    @Test
    void derivesTheOriginsFromTheIssuerAndTheAuthorizationAddress() {
        when(repository.findAll()).thenReturn(List.of(platform("https://moodle.example.org/", "https://Moodle.example.org/mod/lti/auth.php"),
                platform(null, "https://lms.example.com:8443/auth"), platform("not a url", "ftp://files.example.org/x")));

        assertThat(configurer.getPlatformOrigins()).containsExactly("https://lms.example.com:8443", "https://moodle.example.org");
    }

    @Test
    void hasNoOriginsWhileNoPlatformIsConfigured() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(configurer.getPlatformOrigins()).isEmpty();
    }

    @Test
    void readsThePlatformsOnlyOnceWithinTheCacheDuration() {
        when(repository.findAll()).thenReturn(List.of(platform(null, "https://moodle.example.org/auth")));

        configurer.getPlatformOrigins();
        configurer.getPlatformOrigins();

        verify(repository, times(1)).findAll();
    }

    @Test
    void keepsTheLastOriginsWhenThePlatformsCannotBeRead() {
        when(repository.findAll()).thenReturn(List.of(platform(null, "https://moodle.example.org/auth"))).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(configurer.getPlatformOrigins()).containsExactly("https://moodle.example.org");
        ReflectionTestUtils.setField(configurer, "cachedPlatformOriginsAtNanos", System.nanoTime() - Duration.ofMinutes(2).toNanos());

        assertThat(configurer.getPlatformOrigins()).containsExactly("https://moodle.example.org");
        verify(repository, times(2)).findAll();
    }
}
