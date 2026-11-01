package de.tum.cit.aet.artemis.core.service.erroranalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSortField;
import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/**
 * Tests which errors count for the release that is running. The release is part of the key of a group, so a deployment needs no record of
 * when it happened, and a restart of the same release keeps counting in the same groups.
 */
class ErrorAnalysisServiceReleaseTest {

    private static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);

    private final DistributedDataProvider cluster = new LocalDataProviderService();

    private ErrorAnalysisService node;

    private ErrorAnalysisBuffer buffer;

    @BeforeEach
    void startRelease106() {
        startNode("10.6");
    }

    /** A node starts with an empty buffer, as after a restart, and the cluster remembers what the nodes before it handed over. */
    private void startNode(String release) {
        buffer = new ErrorAnalysisBuffer(clock);
        node = new ErrorAnalysisService(cluster, buffer, clock, release);
    }

    private List<ErrorGroupDTO> groups(ErrorAnalysisSpan span) {
        return node.getGroups(span, null, ErrorSeverity.WARN, ErrorAnalysisSortField.MESSAGE, true, 0, Integer.MAX_VALUE).groups();
    }

    private void serverError(String message) {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, message);
    }

    @Test
    void theReleaseShowsOnlyTheErrorsOfTheReleaseThatIsRunning() {
        serverError("old release error");
        node.flush();
        clock.advance(Duration.ofHours(3));

        startNode("10.7");
        serverError("new release error");
        node.flush();

        assertThat(groups(ErrorAnalysisSpan.RELEASE)).extracting(ErrorGroupDTO::message).containsExactly("new release error");
        assertThat(groups(ErrorAnalysisSpan.DAY)).extracting(ErrorGroupDTO::message).containsExactly("new release error", "old release error");
    }

    @Test
    void aRestartOfTheSameReleaseKeepsCountingInTheSameGroupAndRemembersTheFirstTime() {
        serverError("boom");
        node.flush();
        clock.advance(Duration.ofHours(5));

        startNode("10.6");
        serverError("boom");
        node.flush();

        assertThat(groups(ErrorAnalysisSpan.RELEASE)).singleElement().satisfies(group -> {
            assertThat(group.count()).isEqualTo(2);
            assertThat(group.firstSeen()).isEqualTo(START);
            assertThat(group.lastSeen()).isEqualTo(START.plus(Duration.ofHours(5)));
        });
    }

    @Test
    void theReleaseHasNoLimitInTimeWhileTheOtherSpansCountBackFromNow() {
        serverError("long running release");
        node.flush();
        clock.advance(Duration.ofDays(20));

        assertThat(groups(ErrorAnalysisSpan.RELEASE)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(1L);
        assertThat(groups(ErrorAnalysisSpan.WEEK)).isEmpty();
        assertThat(groups(ErrorAnalysisSpan.MONTH)).hasSize(1);
    }

    @Test
    void anErrorThatHappenedBeforeADeploymentIsNotPartOfTheNewReleaseEvenInTheSameHour() {
        serverError("shortly before the deployment");
        node.flush();
        clock.advance(Duration.ofMinutes(5));

        startNode("10.7");
        serverError("shortly after the deployment");

        assertThat(groups(ErrorAnalysisSpan.RELEASE)).extracting(ErrorGroupDTO::message).containsExactly("shortly after the deployment");
    }

    @Test
    void theFirstTimeOfAGroupInTheReleaseIsNotTheFirstTimeInAnEarlierRelease() {
        serverError("boom");
        node.flush();
        clock.advance(Duration.ofDays(2));

        startNode("10.7");
        serverError("boom");
        node.flush();

        assertThat(groups(ErrorAnalysisSpan.RELEASE)).singleElement().satisfies(group -> {
            assertThat(group.count()).isEqualTo(1);
            assertThat(group.firstSeen()).isEqualTo(START.plus(Duration.ofDays(2)));
        });
    }

    @Test
    void theOtherSpansAddUpTheReleasesAgainSoAGroupIsOneRow() {
        serverError("boom");
        node.flush();
        clock.advance(Duration.ofHours(2));

        startNode("10.7");
        serverError("boom");
        serverError("boom");
        node.flush();

        assertThat(groups(ErrorAnalysisSpan.DAY)).singleElement().satisfies(group -> {
            assertThat(group.count()).isEqualTo(3);
            assertThat(group.firstSeen()).isEqualTo(START);
            assertThat(group.lastSeen()).isEqualTo(START.plus(Duration.ofHours(2)));
        });
        assertThat(groups(ErrorAnalysisSpan.RELEASE)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(2L);
    }

    @Test
    void aClientOfTheRunningReleaseCountsForTheReleaseAndAnyOtherClientDoesNot() {
        node.recordClientError(ErrorSeverity.ERROR, "10.6", "client of this release");
        node.recordClientError(ErrorSeverity.ERROR, "10.5", "client that has been open since an earlier release");
        node.recordClientError(ErrorSeverity.ERROR, null, "client without a version");
        node.recordClientError(ErrorSeverity.ERROR, "999.fake", "client with a version that nobody runs");
        node.flush();

        assertThat(groups(ErrorAnalysisSpan.RELEASE)).extracting(ErrorGroupDTO::message).containsExactly("client of this release");
        assertThat(groups(ErrorAnalysisSpan.DAY)).hasSize(4);
    }

    @Test
    void theVersionsThatClientsClaimCannotMakeMoreGroupsThanTheMessagesDo() {
        for (int i = 0; i < 50; i++) {
            node.recordClientError(ErrorSeverity.ERROR, "fake-" + i, "same message");
        }
        node.flush();

        assertThat(groups(ErrorAnalysisSpan.DAY)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(50L);
    }

    @Test
    void whatTheNodeHasNotHandedOverYetCountsForTheReleaseToo() {
        serverError("not flushed yet");

        assertThat(groups(ErrorAnalysisSpan.RELEASE)).extracting(ErrorGroupDTO::message).containsExactly("not flushed yet");
    }
}
