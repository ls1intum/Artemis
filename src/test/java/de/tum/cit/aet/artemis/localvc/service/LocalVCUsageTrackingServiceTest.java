package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsageCollector;

/**
 * Tests what the git path records, and above all that it cannot break the git operation it measures.
 * <p>
 * Both callers invoke this from a {@code finally} block wrapped around the transfer itself, on the git request thread.
 * Nothing here is asynchronous, so a throw would reach the client as a failed clone or push.
 */
class LocalVCUsageTrackingServiceTest {

    private FeatureUsageCollector collector;

    private LocalVCServletService servletService;

    private LocalVCUsageTrackingService service;

    @BeforeEach
    void init() {
        collector = mock(FeatureUsageCollector.class);
        servletService = mock(LocalVCServletService.class);
        when(collector.isEnabled()).thenReturn(true);
        service = new LocalVCUsageTrackingService(Optional.of(collector), servletService);
    }

    @Test
    void shouldRecordAPushOfAStaffRepositoryUnderItsRepositoryType() {
        givenRepositoryPath("/git/COURSE1EX1/course1ex1-tests.git");

        service.recordPush(postRequest(), 12, false);

        verify(collector).recordUsage(eq(FeatureKind.GIT), eq("localvc"), eq("push/tests"), eq(Role.ANONYMOUS), eq(false), anyLong());
    }

    /**
     * The parsed segment is the repository type for staff repositories but the <i>username</i> for student ones, so
     * anything unrecognised has to collapse into one bounded value. Passing it through would turn every student into
     * their own feature and reproduce exactly the unbounded-cardinality problem this analysis exists to avoid.
     */
    @Test
    void shouldCollapseAStudentRepositoryIntoOneBoundedFeature() {
        givenRepositoryPath("/git/COURSE1EX1/course1ex1-ge12abc.git");

        service.recordFetch(postRequest(), 5, false);

        verify(collector).recordUsage(eq(FeatureKind.GIT), eq("localvc"), eq("fetch/assignment"), eq(Role.ANONYMOUS), eq(false), anyLong());
    }

    @Test
    void shouldRecordAFailedTransferAsAFailure() {
        givenRepositoryPath("/git/COURSE1EX1/course1ex1-ge12abc.git");

        service.recordFetch(postRequest(), 5, true);

        verify(collector).recordUsage(eq(FeatureKind.GIT), eq("localvc"), eq("fetch/assignment"), eq(Role.ANONYMOUS), eq(true), anyLong());
    }

    /**
     * A clone or a push is three HTTP requests: two handshakes on {@code /info/refs} and one data transfer. Counting all
     * of them would inflate git usage threefold and count abandoned handshakes as real use.
     */
    @Test
    void shouldCountOnlyTheDataTransferAndNotTheHandshakes() {
        var handshake = new MockHttpServletRequest("GET", "/git/COURSE1EX1/course1ex1-tests.git/info/refs");

        service.recordFetch(handshake, 3, false);

        verify(collector, never()).recordUsage(any(FeatureKind.class), anyString(), anyString(), any(Role.class), anyBoolean(), anyLong());
    }

    @Test
    void shouldReportAnUnparsableUriAsOneBoundedUnknownFeature() {
        when(servletService.parseRepositoryUri(any())).thenThrow(new IllegalArgumentException("not a repository path"));

        service.recordFetch(postRequest(), 5, false);

        verify(collector).recordUsage(eq(FeatureKind.GIT), eq("localvc"), eq("fetch/unknown"), eq(Role.ANONYMOUS), eq(false), anyLong());
    }

    /**
     * The stated invariant of the git path, and the reason the whole method is guarded rather than only its parts: the
     * callers record from a {@code finally} around the transfer, so a throw here would both fail a clone the student is
     * waiting on and discard whatever the transfer was already failing with.
     */
    @Test
    void shouldNotLetAFailingCollectorBreakTheGitOperation() {
        givenRepositoryPath("/git/COURSE1EX1/course1ex1-ge12abc.git");
        when(collector.isEnabled()).thenThrow(new IllegalStateException("the collector is broken"));

        assertThatCode(() -> service.recordFetch(postRequest(), 5, false)).doesNotThrowAnyException();
        assertThatCode(() -> service.recordPush(postRequest(), 5, false)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotLetAFailingRecordingBreakTheGitOperation() {
        givenRepositoryPath("/git/COURSE1EX1/course1ex1-ge12abc.git");
        doThrow(new IllegalStateException("boom")).when(collector).recordUsage(any(), anyString(), anyString(), any(), anyBoolean(), anyLong());

        assertThatCode(() -> service.recordFetch(postRequest(), 5, false)).doesNotThrowAnyException();
    }

    @Test
    void shouldRecordNothingWhenTrackingIsDisabled() {
        when(collector.isEnabled()).thenReturn(false);

        service.recordFetch(postRequest(), 5, false);

        verify(collector, never()).recordUsage(any(FeatureKind.class), anyString(), anyString(), any(Role.class), anyBoolean(), anyLong());
    }

    @Test
    void shouldRecordNothingWhenThereIsNoCollectorAtAll() {
        var withoutCollector = new LocalVCUsageTrackingService(Optional.empty(), servletService);

        assertThatCode(() -> withoutCollector.recordFetch(postRequest(), 5, false)).doesNotThrowAnyException();
        assertThat(Optional.empty()).isEmpty();
    }

    private void givenRepositoryPath(String path) {
        when(servletService.parseRepositoryUri(any())).thenReturn(new LocalVCRepositoryUri(URI.create("http://localhost:8080"), Path.of(path)));
    }

    private static MockHttpServletRequest postRequest() {
        return new MockHttpServletRequest("POST", "/git/COURSE1EX1/course1ex1-tests.git/git-upload-pack");
    }
}
