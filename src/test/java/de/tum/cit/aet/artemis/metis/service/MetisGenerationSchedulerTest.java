package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

/**
 * Unit tests of the poller on the scheduling node: when it ticks into the dispatcher, the reset after the startup on the first tick only, and that it runs as
 * the system and survives a failing tick. The dispatcher is a mock.
 */
class MetisGenerationSchedulerTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-03-02T10:00:00Z");

    private final MetisGenerationDispatchService dispatchService = mock(MetisGenerationDispatchService.class);

    private final MetisGenerationScheduler scheduler = new MetisGenerationScheduler(MetisTestProperties.defaults(), dispatchService);

    @BeforeEach
    void freezeClock() {
        TimeUtil.setClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDoNothingWhenTheSchedulerIsSwitchedOff() {
        var switchedOff = new MetisGenerationScheduler(MetisTestProperties.with(Map.of("generation.scheduler-enabled", "false")), dispatchService);

        switchedOff.tick();
        switchedOff.tick();

        verifyNoInteractions(dispatchService);
    }

    @Test
    void shouldDispatchWithTheCurrentTime() {
        scheduler.tick();

        verify(dispatchService).dispatch(NOW);
    }

    @Test
    void shouldResetAfterTheStartupOnTheFirstTickOnlyAndBeforeTheDispatch() {
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        InOrder order = inOrder(dispatchService);
        order.verify(dispatchService).resetAfterStartup();
        order.verify(dispatchService, times(3)).dispatch(NOW);
        verify(dispatchService, times(1)).resetAfterStartup();
    }

    @Test
    void shouldRetryTheResetOnTheNextTickWhenItFailed() {
        doThrow(new IllegalStateException("database unavailable")).doNothing().when(dispatchService).resetAfterStartup();

        scheduler.tick();

        verify(dispatchService).resetAfterStartup();
        verify(dispatchService, never()).dispatch(any());

        scheduler.tick();
        scheduler.tick();

        verify(dispatchService, times(2)).resetAfterStartup();
        verify(dispatchService, times(2)).dispatch(NOW);
    }

    @Test
    void shouldSwallowAFailingDispatchAndTickAgainAfterwards() {
        doThrow(new IllegalStateException("database unavailable")).doNothing().when(dispatchService).dispatch(any());

        assertThatCode(scheduler::tick).doesNotThrowAnyException();
        assertThatCode(scheduler::tick).doesNotThrowAnyException();

        verify(dispatchService, times(2)).dispatch(NOW);
        verify(dispatchService, times(1)).resetAfterStartup();
    }

    @Test
    void shouldRunAsTheSystemAndRestoreTheContextOfTheThreadAfterwards() {
        Authentication leftover = new UsernamePasswordAuthenticationToken("leftover", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(leftover);
        AtomicReference<Authentication> duringReset = new AtomicReference<>();
        AtomicReference<Authentication> duringDispatch = new AtomicReference<>();
        doAnswer(invocation -> {
            duringReset.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(dispatchService).resetAfterStartup();
        doAnswer(invocation -> {
            duringDispatch.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(dispatchService).dispatch(any());

        scheduler.tick();

        assertThat(duringReset.get()).isNotNull().isNotSameAs(leftover);
        assertThat(duringDispatch.get()).isNotNull().isNotSameAs(leftover);
        assertThat(duringDispatch.get().getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(leftover);
    }
}
