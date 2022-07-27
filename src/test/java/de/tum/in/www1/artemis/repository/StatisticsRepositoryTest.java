package de.tum.in.www1.artemis.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.PersistentAuditEvent;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.security.SecurityUtils;

class StatisticsRepositoryTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private StatisticsRepository statisticsRepository;

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    private ZonedDateTime startDate;

    @BeforeEach
    void setup() {
        startDate = ZonedDateTime.of(2021, 11, 15, 0, 0, 0, 0, ZonedDateTime.now().getZone());
    }

    /**
     * Tests that filterDuplicatedUsers() works as intended for logged in students with weekly and quarterly view.
     * @param spanType the different views (either weekly or quarterly)
     */
    @ParameterizedTest
    @EnumSource(value = SpanType.class, names = { "WEEK", "QUARTER" })
    void testFilterDuplicatedUsers_GraphType_LoggedInUsers(SpanType spanType) {
        // we need an authorization object for the database queries
        SecurityUtils.setAuthorizationObject();
        // end date for the method call
        var endDate = spanType == SpanType.WEEK ? ZonedDateTime.of(2021, 11, 21, 23, 59, 59, 0, startDate.getZone())
                : ZonedDateTime.of(2022, 2, 6, 23, 59, 59, 0, startDate.getZone());
        // we need to add users in order to get non-empty results returned
        database.addUsers(2, 0, 0, 0);
        // the persistentEvents simulate a log in of a student
        // here we simulate that student1 logged in on 15.11.21
        var persistentEventStudent1 = setupPersistentEvent("student1", startDate);
        // here we simulate student1 logged in again on 19.11.21 for the weekly view and for the quarter view a login on 15.01.22
        var persistentEventStudent1Later = spanType == SpanType.WEEK ? setupPersistentEvent("student1", startDate.plusDays(4))
                : setupPersistentEvent("student1", startDate.plusMonths(2));
        // here we simulate that student2 logged in on 19.11.21
        var persistentEventStudent2 = setupPersistentEvent("student2", startDate.plusDays(4));
        // we simulate the same case again in order to have duplication in the result of the query
        var persistentEventStudent2Duplicate = setupPersistentEvent("student2", startDate.plusDays(4).plusHours(2));
        // save the events
        persistenceAuditEventRepository.saveAll(List.of(persistentEventStudent1, persistentEventStudent1Later, persistentEventStudent2, persistentEventStudent2Duplicate));
        // this is the entry that should be returned by both span types
        StatisticsEntry entry191121 = new StatisticsEntry(ZonedDateTime.of(2021, 11, 19, 0, 0, 0, 0, startDate.getZone()), 2);

        // needed as entry method due to private
        List<StatisticsEntry> entryList = statisticsRepository.getNumberOfEntriesPerTimeSlot(GraphType.LOGGED_IN_USERS, spanType, startDate, endDate, StatisticsView.ARTEMIS, null);

        if (spanType == SpanType.WEEK) {
            StatisticsEntry entry151121 = new StatisticsEntry(startDate, 1);
            assertThat(entryList).as("Result contains the entry for 15.11.21").anyMatch((entry) -> compareStatisticsEntries(entry, entry151121));
        }
        else {
            StatisticsEntry entry150122 = new StatisticsEntry(ZonedDateTime.of(2022, 1, 15, 0, 0, 0, 0, startDate.getZone()), 1);
            assertThat(entryList).as("Result contains the entry for 15.01.22").anyMatch((entry) -> compareStatisticsEntries(entry, entry150122));

        }

        assertThat(entryList).as("Result has 2 entries for two time slots").hasSize(2);
        assertThat(entryList).as("Result contains the entry for 19.11.21").anyMatch((entry) -> compareStatisticsEntries(entry, entry191121));

        database.resetDatabase();
        persistenceAuditEventRepository.deleteAll();
    }

    /**
     * Tests how getNumberOfEntriesPerTimeSlot() handles views that are not expected for on different graph types
     * @param graphType The graph type that is tested. Note that not all possible graph types are tested, as some cover every possible view in the code already
     */
    @ParameterizedTest
    @EnumSource(value = GraphType.class, names = { "RELEASED_EXERCISES", "EXERCISES_DUE", "CONDUCTED_EXAMS", "EXAM_PARTICIPATIONS", "EXAM_REGISTRATIONS", "POSTS",
            "RESOLVED_POSTS" })
    void testGetNumberOfEntriesPerTimeSlot_forInvalidView(GraphType graphType) {
        var endDate = startDate.plusDays(7);

        // depending on the graph type, we inject the view that is not supported for it
        StatisticsView view = graphType == GraphType.POSTS || graphType == GraphType.RESOLVED_POSTS ? StatisticsView.ARTEMIS : StatisticsView.EXERCISE;
        assertThrows(UnsupportedOperationException.class, () -> statisticsRepository.getNumberOfEntriesPerTimeSlot(graphType, SpanType.WEEK, startDate, endDate, view, null));
    }

    /**
     * Tests mergeResultsIntoArrayForYear() if start date is in a different year than the statistics entry date
     */
    @Test
    void testDSortDataIntoMonths_differentYear() {
        List<StatisticsEntry> outcome = setupStatisticsEntryList();
        // the start time is in a different year
        ZonedDateTime date = ZonedDateTime.of(2021, 12, 1, 0, 0, 0, 0, startDate.getZone());
        List<Integer> resultYear = Arrays.asList(0, 0, 0, 0, 42, 0, 0, 0, 0, 0, 0, 0);
        List<Integer> expectedResultYear = Arrays.asList(0, 0, 0, 123, 42, 0, 0, 0, 0, 0, 0, 0);
        statisticsRepository.sortDataIntoMonths(outcome, resultYear, date);

        assertThat(resultYear).as("Bucket 4 now has value for the entry date (123)").isEqualTo(expectedResultYear);
    }

    /**
     * Tests mergeResultsIntoArrayForQuarter() if start date is in a different year than the statistics entry date
     */
    @Test
    void testSortDataIntoWeeks_differentYear() {
        // the start time is in a different year
        List<StatisticsEntry> outcome = setupStatisticsEntryList();
        List<Integer> resultYear = new ArrayList<>();
        List<Integer> expectedResultYear = new ArrayList<>();
        for (int i = 0; i < 53; i++) {
            resultYear.add(0);
            expectedResultYear.add(i != 15 ? 0 : 123);
        }

        statisticsRepository.sortDataIntoWeeks(outcome, resultYear, startDate);

        assertThat(resultYear).as("Bucket 15 now has value for the entry date (123)").isEqualTo(expectedResultYear);
    }

    /**
     * A helper method in order to prevent code duplication for comparison of StatisticEntries
     * @param entry1 the first entry to compare
     * @param entry2 the second entry to compare
     * @return true if both entries contain the same day and amount, false otherwise
     */
    private boolean compareStatisticsEntries(StatisticsEntry entry1, StatisticsEntry entry2) {
        return entry1.getDay().toString().equals(entry2.getDay().toString()) && entry1.getAmount() == entry2.getAmount();
    }

    /**
     * Setup method in order to prevent code duplication for initialisation of entry list
     * @return entry list used as input for tests
     */
    private List<StatisticsEntry> setupStatisticsEntryList() {
        StatisticsEntry entry = new StatisticsEntry();
        ZonedDateTime date = ZonedDateTime.of(2022, 3, 4, 23, 59, 59, 0, startDate.getZone());
        entry.setDay(date);
        entry.setAmount(123);
        var list = new ArrayList<StatisticsEntry>();
        list.add(entry);

        return list;
    }

    /**
     * Creates persistent event for tests
     * @param principal the student login that should be used
     * @param date the timestamp the login should be simulated
     * @return PersistentAuditEvent representing the login of the given user for at the given point in time
     */
    private PersistentAuditEvent setupPersistentEvent(String principal, ZonedDateTime date) {
        PersistentAuditEvent persistentEvent = new PersistentAuditEvent();
        persistentEvent.setPrincipal(principal);
        persistentEvent.setAuditEventType("AUTHENTICATION_SUCCESS");
        persistentEvent.setAuditEventDate(Instant.from(date));

        return persistentEvent;
    }
}
