package de.tum.cit.aet.artemis.core.service.erroranalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

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
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/**
 * Two nodes share one distributed data provider and have a buffer of their own, as two core nodes of a cluster do.
 */
class ErrorAnalysisServiceTest {

    private static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    /** The release that the nodes run. */
    private static final String RELEASE = "10.6";

    private final MutableClock clock = new MutableClock(START);

    private final DistributedDataProvider cluster = new LocalDataProviderService();

    private ErrorAnalysisBuffer bufferOfNode1;

    private ErrorAnalysisBuffer bufferOfNode2;

    private ErrorAnalysisService node1;

    private ErrorAnalysisService node2;

    @BeforeEach
    void setUp() {
        bufferOfNode1 = new ErrorAnalysisBuffer(clock);
        bufferOfNode2 = new ErrorAnalysisBuffer(clock);
        node1 = new ErrorAnalysisService(cluster, bufferOfNode1, clock, RELEASE);
        node2 = new ErrorAnalysisService(cluster, bufferOfNode2, clock, RELEASE);
    }

    /** All groups of a query, sorted the way the dashboard shows them by default. */
    private static List<ErrorGroupDTO> query(ErrorAnalysisService node, ErrorAnalysisSpan span, ErrorLocation location, ErrorSeverity minSeverity) {
        return node.getGroups(span, location, minSeverity, ErrorAnalysisSortField.COUNT, false, 0, Integer.MAX_VALUE).groups();
    }

    private static List<ErrorGroupDTO> errorsOfTheLastDay(ErrorAnalysisService node) {
        return query(node, ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR);
    }

    @Test
    void everyNodeAnswersWithTheErrorsOfAllNodes() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "Could not find exercise 17");
        clock.advance(Duration.ofHours(2));
        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "Could not find exercise 42");
        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "Could not find exercise 43");
        node1.flush();
        node2.flush();

        for (ErrorAnalysisService asked : List.of(node1, node2)) {
            assertThat(errorsOfTheLastDay(asked)).singleElement().satisfies(group -> {
                assertThat(group.message()).isEqualTo("Could not find exercise <n>");
                assertThat(group.count()).isEqualTo(3);
                assertThat(group.firstSeen()).isEqualTo(START);
                assertThat(group.lastSeen()).isEqualTo(START.plus(Duration.ofHours(2)));
            });
        }
    }

    @Test
    void aNodeAlsoSeesWhatItHasNotHandedToTheClusterYetButNotWhatAnotherNodeHasNotHanded() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "only on the first node");
        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "only on the second node");

        assertThat(errorsOfTheLastDay(node1)).extracting(ErrorGroupDTO::message).containsExactly("only on the first node");
        assertThat(errorsOfTheLastDay(node2)).extracting(ErrorGroupDTO::message).containsExactly("only on the second node");

        node1.flush();
        node2.flush();

        assertThat(errorsOfTheLastDay(node1)).extracting(ErrorGroupDTO::message).containsExactlyInAnyOrder("only on the first node", "only on the second node");
    }

    @Test
    void doesNotCountAnErrorTwiceWhenItIsFlushedAndQueriedAgain() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        node1.flush();
        node1.flush();

        assertThat(errorsOfTheLastDay(node1)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(1L);
    }

    @Test
    void addsTheCountsOfFlushesThatFollowEachOther() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        node1.flush();
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        node1.flush();
        node2.flush();

        assertThat(errorsOfTheLastDay(node2)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(3L);
    }

    @Test
    void recordsAnErrorThatAClientReported() {
        node1.recordClientError(ErrorSeverity.CRITICAL, RELEASE, "Failed to bootstrap");
        node1.flush();

        assertThat(errorsOfTheLastDay(node2)).singleElement().satisfies(group -> {
            assertThat(group.location()).isEqualTo(ErrorLocation.CLIENT);
            assertThat(group.severity()).isEqualTo(ErrorSeverity.CRITICAL);
        });
    }

    @Test
    void countsOnlyTheErrorsOfTheSelectedTimeSpan() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "old and new");
        node1.flush();
        clock.advance(Duration.ofDays(3));
        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "old and new");
        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "only recent");
        node2.flush();
        clock.advance(Duration.ofHours(2));

        assertThat(errorsOfTheLastDay(node1)).extracting(ErrorGroupDTO::message, ErrorGroupDTO::count).containsExactlyInAnyOrder(tuple("old and new", 1L),
                tuple("only recent", 1L));
        assertThat(query(node1, ErrorAnalysisSpan.WEEK, null, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message, ErrorGroupDTO::count)
                .containsExactlyInAnyOrder(tuple("old and new", 2L), tuple("only recent", 1L));
    }

    @Test
    void keepsTheFirstSeenOfAGroupThatIsOlderThanTheTimeSpan() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        node1.flush();
        clock.advance(Duration.ofDays(3));
        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        node2.flush();

        assertThat(errorsOfTheLastDay(node1)).singleElement().satisfies(group -> {
            assertThat(group.firstSeen()).isEqualTo(START);
            assertThat(group.count()).isEqualTo(1);
        });
    }

    @Test
    void leavesOutAGroupThatDidNotHappenInTheTimeSpan() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "long ago");
        node1.flush();
        clock.advance(Duration.ofDays(10));

        assertThat(query(node2, ErrorAnalysisSpan.WEEK, null, ErrorSeverity.ERROR)).isEmpty();
        assertThat(query(node2, ErrorAnalysisSpan.MONTH, null, ErrorSeverity.ERROR)).hasSize(1);
    }

    @Test
    void filtersByLocationAndByMinimumSeverity() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "server error");
        bufferOfNode2.record(ErrorLocation.CLIENT, ErrorSeverity.WARN, null, "client warning");
        bufferOfNode2.record(ErrorLocation.CLIENT, ErrorSeverity.CRITICAL, null, "client critical");
        node1.flush();
        node2.flush();

        assertThat(query(node1, ErrorAnalysisSpan.DAY, null, ErrorSeverity.WARN)).extracting(ErrorGroupDTO::message).containsExactlyInAnyOrder("server error", "client warning",
                "client critical");
        assertThat(query(node1, ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message).containsExactlyInAnyOrder("server error", "client critical");
        assertThat(query(node1, ErrorAnalysisSpan.DAY, ErrorLocation.CLIENT, ErrorSeverity.WARN)).extracting(ErrorGroupDTO::message).containsExactlyInAnyOrder("client warning",
                "client critical");
        assertThat(query(node1, ErrorAnalysisSpan.DAY, ErrorLocation.SERVER, ErrorSeverity.CRITICAL)).isEmpty();
    }

    @Test
    void listsTheMostFrequentGroupFirstAndTheMostRecentOfEqualOnes() {
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "rare and old");
        clock.advance(Duration.ofMinutes(1));
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "rare and new");
        for (int i = 0; i < 3; i++) {
            bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "frequent");
        }
        node1.flush();

        assertThat(errorsOfTheLastDay(node2)).extracting(ErrorGroupDTO::message).containsExactly("frequent", "rare and new", "rare and old");
    }

    @Test
    void keepsAtMostTheAllowedNumberOfGroupsInTheClusterAndForgetsTheOneSeenLongestAgo() {
        for (int i = 0; i < ErrorAnalysisBuffer.MAX_GROUPS; i++) {
            bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "error kind " + (char) ('a' + i % 26) + (char) ('a' + i / 26 % 26) + (char) ('a' + i / 676));
            clock.advance(Duration.ofSeconds(1));
        }
        node1.flush();
        assertThat(errorsOfTheLastDay(node2)).hasSize(ErrorAnalysisBuffer.MAX_GROUPS);

        bufferOfNode2.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "one more");
        node2.flush();

        List<ErrorGroupDTO> groups = errorsOfTheLastDay(node1);
        assertThat(groups).hasSize(ErrorAnalysisBuffer.MAX_GROUPS);
        assertThat(groups).extracting(ErrorGroupDTO::message).contains("one more");
        assertThat(groups).extracting(ErrorGroupDTO::firstSeen).doesNotContain(START);
    }

    @Test
    void anErrorIsNotLostAndNotCountedTwiceWhenTheClusterFailsInTheMiddleOfAFlush() {
        DistributedDataProvider unreliableCluster = spy(cluster);
        DistributedMap<String, Long> counts = spy(cluster.<String, Long>getExpiringMap("error-analysis-counts", Duration.ofDays(31)));
        doReturn(counts).when(unreliableCluster).getExpiringMap(eq("error-analysis-counts"), any());
        ErrorAnalysisService unreliableNode = new ErrorAnalysisService(unreliableCluster, bufferOfNode1, clock, RELEASE);

        // two hours of one group: the first hour is written, the second fails
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        clock.advance(Duration.ofHours(1));
        bufferOfNode1.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "boom");
        doCallRealMethod().doThrow(new IllegalStateException("cluster not reachable")).doCallRealMethod().when(counts).putIfAbsent(anyString(), any(Long.class),
                any(Duration.class));

        unreliableNode.flush();

        // nothing is lost: what was not written is still counted, as the buffer of the node
        assertThat(errorsOfTheLastDay(unreliableNode)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(2L);

        unreliableNode.flush();

        // and the hour that was written is not written again
        assertThat(errorsOfTheLastDay(node2)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(2L);
    }
}
