package de.tum.cit.aet.artemis.lti.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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

    @Test
    void afterAFailedRefreshTheNextAttemptWaitsForTheCacheDuration() {
        when(repository.findAll()).thenReturn(List.of(platform(null, "https://moodle.example.org/auth"))).thenThrow(new IllegalStateException("database unavailable"));
        configurer.getPlatformOrigins();
        ReflectionTestUtils.setField(configurer, "cachedPlatformOriginsAtNanos", System.nanoTime() - Duration.ofMinutes(2).toNanos());

        configurer.getPlatformOrigins();
        configurer.getPlatformOrigins();
        configurer.getPlatformOrigins();

        // the failed read counts as a refresh, so the three requests during the outage share one query
        verify(repository, times(2)).findAll();
    }

    @Test
    void aRequestDuringARefreshGetsTheCachedOriginsInsteadOfQueryingAgain() throws Exception {
        when(repository.findAll()).thenReturn(List.of(platform(null, "https://moodle.example.org/auth")));
        configurer.getPlatformOrigins();
        ReflectionTestUtils.setField(configurer, "cachedPlatformOriginsAtNanos", System.nanoTime() - Duration.ofMinutes(2).toNanos());
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        when(repository.findAll()).thenAnswer(invocation -> {
            refreshStarted.countDown();
            releaseRefresh.await(10, TimeUnit.SECONDS);
            return List.of(platform(null, "https://lms.example.com/auth"));
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Set<String>> refreshing = executor.submit(configurer::getPlatformOrigins);
            assertThat(refreshStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // the second request does not wait for the refresh and does not query the database
            assertThat(configurer.getPlatformOrigins()).containsExactly("https://moodle.example.org");

            releaseRefresh.countDown();
            assertThat(refreshing.get(10, TimeUnit.SECONDS)).containsExactly("https://lms.example.com");
            verify(repository, times(2)).findAll();
        }
        finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void warnsOncePerPlatformWithoutADerivableOrigin() {
        when(repository.findAll()).thenReturn(List.of(platform("not a url", "ftp://files.example.org/x")));
        Logger logger = (Logger) LoggerFactory.getLogger(CustomLti13Configurer.class);
        // another test of the same JVM may have changed the level of this logger
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            configurer.getPlatformOrigins();
            ReflectionTestUtils.setField(configurer, "cachedPlatformOriginsAtNanos", System.nanoTime() - Duration.ofMinutes(2).toNanos());
            configurer.getPlatformOrigins();

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("artemis.security.frame-ancestors");
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }
}
