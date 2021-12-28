package de.tum.in.www1.artemis.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.PersistentAuditEvent;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;

public class StatisticsRepositoryTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private StatisticsRepository statisticsRepository;

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    private ZonedDateTime startDate;

    @BeforeEach
    public void setup() {
        startDate = ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]");
    }

    /**
     * Tests that filterDuplicatedUsers() works as intended for logged in students with weekly and quarterly view.
     * @param spanType the different views (either weekly or quarterly)
     */
    @ParameterizedTest
    @EnumSource(value = SpanType.class, names = { "WEEK", "QUARTER" })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testFilterDuplicatedUsers_GraphType_LoggedInUsers(SpanType spanType) {
        // end date for the method call
        var endDate = spanType == SpanType.WEEK ? startDate.plusDays(7) : startDate.plusYears(1);
        // we need to add users in order to get non-empty results returned
        database.addUsers(2, 0, 0, 0);
        // the persistentEvents simulate a log in of a student
        // here we simulate that student1 logged in on 03.12.07
        var persistentEventStudent1 = setupPersistentEvent("student1", startDate);
        // here we simulate student1 logged in again on 07.12.07 for the weekly view and for the quarter view a login on 03.02.08
        var persistentEventStudent1Later = spanType == SpanType.WEEK ? setupPersistentEvent("student1", startDate.plusDays(4))
                : setupPersistentEvent("student1", startDate.plusMonths(2));
        // here we simulate that student2 logged in on 07.12.07
        var persistentEventStudent2 = setupPersistentEvent("student2", startDate.plusDays(4));
        // we simulate the same case again in order to have duplication in the result of the query
        var persistentEventStudent2Duplicate = setupPersistentEvent("student2", startDate.plusDays(4).plusHours(2));
        // save the events
        persistenceAuditEventRepository.saveAll(List.of(persistentEventStudent1, persistentEventStudent1Later, persistentEventStudent2, persistentEventStudent2Duplicate));
        // this is the entry that should be returned by both span types
        StatisticsEntry entry031207 = new StatisticsEntry();
        entry031207.setDay(ZonedDateTime.parse("2007-12-03T00:00:00+01:00[Europe/Paris]"));

        // needed as entry method due to private
        List<StatisticsEntry> entryList = statisticsRepository.getNumberOfEntriesPerTimeSlot(spanType, startDate, endDate, GraphType.LOGGED_IN_USERS, StatisticsView.ARTEMIS, null);

        if (spanType == SpanType.WEEK) {
            StatisticsEntry entry071207 = new StatisticsEntry(ZonedDateTime.parse("2007-12-07T00:00:00+01:00[Europe/Paris]"), 2);
            entry031207.setAmount(1);

            assertThat(entryList).as("Result contains the entry for 07.12.07").anyMatch((entry) -> compareStatisticsEntries(entry, entry071207));
        }
        else {
            entry031207.setAmount(2);
            StatisticsEntry entry290108 = new StatisticsEntry(ZonedDateTime.parse("2008-01-29T00:00+01:00[Europe/Paris]"), 1);

            assertThat(entryList).as("Result contains the entry for 29.01.08").anyMatch((entry) -> compareStatisticsEntries(entry, entry290108));
        }

        assertThat(entryList).as("Result has 2 entries for two time slots").hasSize(2);
        assertThat(entryList).as("Result contains the entry for 03.12.07").anyMatch((entry) -> compareStatisticsEntries(entry, entry031207));

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
    public void testGetNumberOfEntriesPerTimeSlot_forInvalidView(GraphType graphType) {
        var endDate = startDate.plusDays(7);

        // depending on the graph type, we inject the view that is not supported for it
        StatisticsView view = graphType == GraphType.POSTS || graphType == GraphType.RESOLVED_POSTS ? StatisticsView.ARTEMIS : StatisticsView.EXERCISE;
        assertThrows(UnsupportedOperationException.class, () -> statisticsRepository.getNumberOfEntriesPerTimeSlot(SpanType.WEEK, startDate, endDate, graphType, view, null));
    }

    /**
     * Tests mergeResultsIntoArrayForYear() if start date is in a different year than the statistics entry date
     */
    @Test
    public void testMergeResultsIntoArrayForYear_differentYear_and_differentQuarter() {
        List<StatisticsEntry> outcome = setupStatisticsEntryList();
        // the start time is in a different year
        ZonedDateTime startDate = ZonedDateTime.parse("2020-12-03T10:15:30+01:00[Europe/Paris]");
        Integer[] resultYear = { 0, 0, 0, 0, 42, 0, 0, 0, 0, 0, 0, 0 };
        Integer[] expectedResultYear = { 0, 0, 0, 0, 42, 0, 0, 0, 0, 0, 0, 123 };
        Integer[] returnedResultYear = statisticsRepository.mergeResultsIntoArrayForYear(outcome, resultYear, startDate);

        assertThat(returnedResultYear).as("Bucket 11 now has value for the entry date (123)").isEqualTo(expectedResultYear);
    }

    /**
     * Tests mergeResultsIntoArrayForQuarter() if start date is in a different year than the statistics entry date
     */
    @Test
    public void testMergeResultsIntoArrayForQuarter_differentYear() {
        startDate.plusYears(13);
        List<StatisticsEntry> outcome = setupStatisticsEntryList();
        // the start time is in a different year
        Integer[] resultQuarter = new Integer[53];
        Integer[] expectedResultQuarter = new Integer[53];
        for (int i = 0; i < 53; i++) {
            resultQuarter[i] = 0;
            expectedResultQuarter[i] = i != 48 ? 0 : 123;
        }

        Integer[] returnedResultQuarter = statisticsRepository.mergeResultsIntoArrayForQuarter(outcome, resultQuarter, startDate);

        assertThat(returnedResultQuarter).as("Bucket 48 now has value for the entry date (123)").isEqualTo(expectedResultQuarter);
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
        ZonedDateTime date = ZonedDateTime.parse("2021-11-03T10:15:30+01:00[Europe/Paris]");
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
