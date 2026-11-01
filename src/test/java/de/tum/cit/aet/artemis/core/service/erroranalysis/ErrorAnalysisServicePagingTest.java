package de.tum.cit.aet.artemis.core.service.erroranalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSortField;
import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorAnalysisPageDTO;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/**
 * Tests that the groups are sorted first and cut into pages afterwards, so that a page is a part of the order that was asked for.
 */
class ErrorAnalysisServicePagingTest {

    private static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);

    private final ErrorAnalysisBuffer buffer = new ErrorAnalysisBuffer(clock);

    private final ErrorAnalysisService service = new ErrorAnalysisService(new LocalDataProviderService(), buffer, clock, "10.6");

    /** Seven groups: "error a" happened once, "error b" twice and so on, each a minute after the one before, so the counts and the times differ. */
    @BeforeEach
    void sevenGroups() {
        String[] names = { "a", "b", "c", "d", "e", "f", "g" };
        for (int i = 0; i < names.length; i++) {
            for (int times = 0; times <= i; times++) {
                buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, null, "error " + names[i]);
            }
            clock.advance(Duration.ofMinutes(1));
        }
        service.flush();
    }

    private ErrorAnalysisPageDTO page(ErrorAnalysisSortField sortField, boolean ascending, int page, int size) {
        return service.getGroups(ErrorAnalysisSpan.RELEASE, null, ErrorSeverity.ERROR, sortField, ascending, page, size);
    }

    private static List<String> messages(ErrorAnalysisPageDTO page) {
        return page.groups().stream().map(ErrorGroupDTO::message).toList();
    }

    @Test
    void cutsTheSortedGroupsIntoPages() {
        assertThat(messages(page(ErrorAnalysisSortField.COUNT, false, 0, 3))).containsExactly("error g", "error f", "error e");
        assertThat(messages(page(ErrorAnalysisSortField.COUNT, false, 1, 3))).containsExactly("error d", "error c", "error b");
        assertThat(messages(page(ErrorAnalysisSortField.COUNT, false, 2, 3))).containsExactly("error a");
    }

    @Test
    void thePagesFollowTheDirectionOfTheSortOrder() {
        assertThat(messages(page(ErrorAnalysisSortField.COUNT, true, 0, 3))).containsExactly("error a", "error b", "error c");
        assertThat(messages(page(ErrorAnalysisSortField.COUNT, true, 2, 3))).containsExactly("error g");
    }

    @Test
    void thePagesOfAnyOrderDoNotOverlapAndTogetherAreEveryGroupOnce() {
        for (ErrorAnalysisSortField field : ErrorAnalysisSortField.values()) {
            for (boolean ascending : new boolean[] { true, false }) {
                List<String> all = new ArrayList<>();
                for (int page = 0; page < 4; page++) {
                    all.addAll(messages(page(field, ascending, page, 2)));
                }
                assertThat(all).as("%s %s", field, ascending ? "ascending" : "descending").doesNotHaveDuplicates().hasSize(7);
            }
        }
    }

    @Test
    void theTotalsAreThoseOfAllPagesWhicheverPageIsAsked() {
        for (int page = 0; page < 4; page++) {
            ErrorAnalysisPageDTO result = page(ErrorAnalysisSortField.COUNT, false, page, 3);
            assertThat(result.totalGroups()).isEqualTo(7);
            // 1 + 2 + ... + 7
            assertThat(result.totalErrors()).isEqualTo(28);
        }
    }

    @Test
    void aPageBeyondTheLastIsEmptyButKnowsTheTotals() {
        ErrorAnalysisPageDTO result = page(ErrorAnalysisSortField.COUNT, false, 10, 3);

        assertThat(result.groups()).isEmpty();
        assertThat(result.totalGroups()).isEqualTo(7);
    }

    @Test
    void sortsByTheMessage() {
        assertThat(messages(page(ErrorAnalysisSortField.MESSAGE, true, 0, 2))).containsExactly("error a", "error b");
        assertThat(messages(page(ErrorAnalysisSortField.MESSAGE, false, 0, 2))).containsExactly("error g", "error f");
    }

    @Test
    void sortsByTheTimesAnErrorWasSeen() {
        // every group was seen for the first and the last time one minute after the group before it
        assertThat(messages(page(ErrorAnalysisSortField.FIRST_SEEN, true, 0, 2))).containsExactly("error a", "error b");
        assertThat(messages(page(ErrorAnalysisSortField.LAST_SEEN, false, 0, 2))).containsExactly("error g", "error f");
    }

    @Test
    void sortsBySeverityAndLocationAndKeepsTheRestInAStableOrder() {
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.CRITICAL, null, "critical client error");
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.WARN, null, "client warning");
        service.flush();

        List<ErrorGroupDTO> bySeverity = service.getGroups(ErrorAnalysisSpan.RELEASE, null, ErrorSeverity.WARN, ErrorAnalysisSortField.SEVERITY, false, 0, 100).groups();
        assertThat(bySeverity.getFirst().severity()).isEqualTo(ErrorSeverity.CRITICAL);
        assertThat(bySeverity.getLast().severity()).isEqualTo(ErrorSeverity.WARN);

        List<ErrorGroupDTO> byLocation = service.getGroups(ErrorAnalysisSpan.RELEASE, null, ErrorSeverity.WARN, ErrorAnalysisSortField.LOCATION, true, 0, 100).groups();
        assertThat(byLocation).extracting(ErrorGroupDTO::location).containsExactly(ErrorLocation.SERVER, ErrorLocation.SERVER, ErrorLocation.SERVER, ErrorLocation.SERVER,
                ErrorLocation.SERVER, ErrorLocation.SERVER, ErrorLocation.SERVER, ErrorLocation.CLIENT, ErrorLocation.CLIENT);
        // among the groups of one location the most recent comes first, in either direction
        assertThat(byLocation.subList(0, 7)).extracting(ErrorGroupDTO::message).containsExactly("error g", "error f", "error e", "error d", "error c", "error b", "error a");
    }

    @Test
    void groupsThatAreEqualInTheSortFieldKeepTheSameOrderWheneverAsked() {
        // the same count for three groups
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "10.6", "tie one");
        clock.advance(Duration.ofMinutes(1));
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "10.6", "tie two");
        clock.advance(Duration.ofMinutes(1));
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "10.6", "tie three");
        service.flush();

        List<String> first = messages(service.getGroups(ErrorAnalysisSpan.RELEASE, ErrorLocation.CLIENT, ErrorSeverity.ERROR, ErrorAnalysisSortField.COUNT, true, 0, 10));
        List<String> second = messages(service.getGroups(ErrorAnalysisSpan.RELEASE, ErrorLocation.CLIENT, ErrorSeverity.ERROR, ErrorAnalysisSortField.COUNT, true, 0, 10));

        assertThat(first).containsExactly("tie three", "tie two", "tie one").isEqualTo(second);
    }

    @Test
    void anAscendingSortKeepsTheMostRecentOfEqualGroupsFirstToo() {
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "10.6", "tie one");
        clock.advance(Duration.ofMinutes(1));
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "10.6", "tie two");
        service.flush();

        assertThat(messages(service.getGroups(ErrorAnalysisSpan.RELEASE, ErrorLocation.CLIENT, ErrorSeverity.ERROR, ErrorAnalysisSortField.COUNT, false, 0, 10)))
                .containsExactly("tie two", "tie one");
    }
}
