package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.core.util.DateUtil.sortDataIntoMonths;
import static de.tum.cit.aet.artemis.core.util.DateUtil.sortDataIntoWeeks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.domain.GraphType;
import de.tum.cit.aet.artemis.admin.domain.PersistentAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.StatisticsView;
import de.tum.cit.aet.artemis.admin.dto.ActiveUserLastSubmissionDTO;
import de.tum.cit.aet.artemis.admin.dto.StatisticsEntry;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.StatisticsRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.domain.SpanType;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class StatisticsRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "statisticsrepository";

    @Autowired
    private StatisticsRepository statisticsRepository;

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ResultTestRepository resultTestRepository;

    @Autowired
    private SubmissionService submissionService;

    private ZonedDateTime startDate;

    @BeforeEach
    void setup() {
        startDate = ZonedDateTime.of(2021, 11, 15, 0, 0, 0, 0, ZonedDateTime.now().getZone());
    }

    /**
     * Tests that filterDuplicatedUsers() works as intended for logged in students with weekly and quarterly view.
     *
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
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        // the persistentEvents simulate a log in of a student
        // here we simulate that student1 logged in on 15.11.21
        var persistentEventStudent1 = setupPersistentEvent(TEST_PREFIX + "student1", startDate);
        // here we simulate student1 logged in again on 19.11.21 for the weekly view and for the quarter view a login on 15.01.22
        var persistentEventStudent1Later = spanType == SpanType.WEEK ? setupPersistentEvent(TEST_PREFIX + "student1", startDate.plusDays(4))
                : setupPersistentEvent(TEST_PREFIX + "student1", startDate.plusMonths(2));
        // here we simulate that student2 logged in on 19.11.21
        var persistentEventStudent2 = setupPersistentEvent(TEST_PREFIX + "student2", startDate.plusDays(4));
        // we simulate the same case again in order to have duplication in the result of the query
        var persistentEventStudent2Duplicate = setupPersistentEvent(TEST_PREFIX + "student2", startDate.plusDays(4).plusHours(2));
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

        persistenceAuditEventRepository.deleteAll();
    }

    /**
     * Tests that users flagged as test users (isTestUser = true) are excluded from the logged-in users statistics,
     * which replaced the previous "login NOT LIKE '%test%'" heuristic.
     */
    @Test
    void testLoggedInUsersExcludesTestUsers() {
        SecurityUtils.setAuthorizationObject();
        var endDate = ZonedDateTime.of(2021, 11, 21, 23, 59, 59, 0, startDate.getZone());
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        // Mark student2 as a test user: it must be excluded from the statistics, even though its login does not contain "test".
        User testUser = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        testUser.setTestUser(true);
        userTestRepository.save(testUser);
        // Both students "log in" on the same day; only the non-test student1 must be counted.
        persistenceAuditEventRepository.saveAll(List.of(setupPersistentEvent(TEST_PREFIX + "student1", startDate), setupPersistentEvent(TEST_PREFIX + "student2", startDate)));

        List<StatisticsEntry> entryList = statisticsRepository.getNumberOfEntriesPerTimeSlot(GraphType.LOGGED_IN_USERS, SpanType.WEEK, startDate, endDate, StatisticsView.ARTEMIS,
                null);

        StatisticsEntry expected = new StatisticsEntry(startDate, 1);
        assertThat(entryList).as("only the non-test user is counted for the slot").anyMatch((entry) -> compareStatisticsEntries(entry, expected));
        assertThat(entryList).as("no slot counts the excluded test user").allSatisfy((entry) -> assertThat(entry.getAmount()).isEqualTo(1));

        persistenceAuditEventRepository.deleteAll();
    }

    /**
     * Tests that example results (from example submissions) are excluded from the created-results statistics.
     * The rewritten queries filter the denormalized {@code r.exerciseId} directly, so example results must be
     * excluded explicitly to preserve the previous behaviour, where the submission → participation → exercise join
     * dropped them (example submissions have no participation).
     */
    @Test
    void testCreatedResultsForCourseExcludesExampleResults() {
        SecurityUtils.setAuthorizationObject();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise exercise = (TextExercise) course.getExercises().iterator().next();
        var now = ZonedDateTime.now();
        var completionDate = now.minusMinutes(30);

        // a normal (student-participation-backed) result -> must be counted
        var normalSubmission = new TextSubmission();
        normalSubmission.setSubmissionDate(now.minusHours(1));
        var savedNormalSubmission = participationUtilService.addSubmission(exercise, normalSubmission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(AssessmentType.MANUAL, completionDate, savedNormalSubmission);

        // a real example submission has no participation; its flagged result must be excluded, exactly as the previous
        // submission -> participation -> exercise join excluded it
        ExampleSubmission exampleSubmission = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("example text", exercise, true));
        assertThat(exampleSubmission.getSubmission().getParticipation()).as("example submissions have no participation").isNull();
        Result exampleResult = submissionService.saveNewEmptyResult(exampleSubmission.getSubmission(), exercise.getId());
        exampleResult.setExampleResult(true);
        exampleResult.setCompletionDate(completionDate);
        resultTestRepository.save(exampleResult);

        List<StatisticsEntry> createdResults = statisticsRepository.getCreatedResultsForCourse(now.minusDays(1), now.plusDays(1), List.of(exercise.getId()));
        long total = createdResults.stream().mapToLong(StatisticsEntry::getAmount).sum();
        assertThat(total).as("example results are excluded from the created-results statistics").isEqualTo(1);
    }

    /**
     * The active-user gauge query must report the latest submission date per student, so that {@code MetricsBean} can
     * bucket every student into the rolling 1/7/14/30 day windows without the database aggregating over
     * {@code jhi_user}.
     */
    @Test
    void testFindLastSubmissionPerActiveUserReportsTheLatestSubmissionDatePerStudent() {
        SecurityUtils.setAuthorizationObject();
        userUtilService.addUsers(TEST_PREFIX, 4, 0, 0, 0);
        TextExercise exercise = getReleasedTextExercise();
        var now = ZonedDateTime.now();
        User student = userTestRepository.findOneByLogin(TEST_PREFIX + "student3").orElseThrow();
        addTextSubmission(exercise, TEST_PREFIX + "student3", now.minusDays(20));
        addTextSubmission(exercise, TEST_PREFIX + "student3", now.minusDays(2));

        var activeUsers = statisticsRepository.findLastSubmissionPerActiveUser(now, now.minusDays(30));

        assertThat(activeUsers).filteredOn(activeUser -> activeUser.userId() == student.getId()).singleElement()
                .satisfies(activeUser -> assertThat(activeUser.lastSubmissionDate().toInstant()).isCloseTo(now.minusDays(2).toInstant(), within(1, ChronoUnit.SECONDS)));
    }

    /**
     * Only the widest window (30 days) is fetched from the database; students whose last submission predates it must
     * not be reported at all.
     */
    @Test
    void testFindLastSubmissionPerActiveUserExcludesSubmissionsBeforeTheWindow() {
        SecurityUtils.setAuthorizationObject();
        userUtilService.addUsers(TEST_PREFIX, 4, 0, 0, 0);
        TextExercise exercise = getReleasedTextExercise();
        var now = ZonedDateTime.now();
        User student = userTestRepository.findOneByLogin(TEST_PREFIX + "student4").orElseThrow();
        addTextSubmission(exercise, TEST_PREFIX + "student4", now.minusDays(40));

        var activeUsers = statisticsRepository.findLastSubmissionPerActiveUser(now, now.minusDays(30));

        assertThat(activeUsers).extracting(ActiveUserLastSubmissionDTO::userId).doesNotContain(student.getId());
    }

    private TextExercise getReleasedTextExercise() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        return (TextExercise) course.getExercises().iterator().next();
    }

    private void addTextSubmission(TextExercise exercise, String login, ZonedDateTime submissionDate) {
        var submission = new TextSubmission();
        submission.setSubmissionDate(submissionDate);
        participationUtilService.addSubmission(exercise, submission, login);
    }

    /**
     * Tests how getNumberOfEntriesPerTimeSlot() handles views that are not expected for on different graph types
     *
     * @param graphType The graph type that is tested. Note that not all possible graph types are tested, as some cover every possible view in the code already
     */
    @ParameterizedTest
    @EnumSource(value = GraphType.class, names = { "RELEASED_EXERCISES", "EXERCISES_DUE", "CONDUCTED_EXAMS", "EXAM_PARTICIPATIONS", "EXAM_REGISTRATIONS", "POSTS",
            "RESOLVED_POSTS" })
    void testGetNumberOfEntriesPerTimeSlot_forInvalidView(GraphType graphType) {
        var endDate = startDate.plusDays(7);

        // depending on the graph type, we inject the view that is not supported for it
        StatisticsView view = graphType == GraphType.POSTS || graphType == GraphType.RESOLVED_POSTS ? StatisticsView.ARTEMIS : StatisticsView.EXERCISE;
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> statisticsRepository.getNumberOfEntriesPerTimeSlot(graphType, SpanType.WEEK, startDate, endDate, view, null));
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
        sortDataIntoMonths(outcome, resultYear, date);

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

        sortDataIntoWeeks(outcome, resultYear, startDate);

        assertThat(resultYear).as("Bucket 15 now has value for the entry date (123)").isEqualTo(expectedResultYear);
    }

    /**
     * A helper method in order to prevent code duplication for comparison of StatisticEntries
     *
     * @param entry1 the first entry to compare
     * @param entry2 the second entry to compare
     * @return true if both entries contain the same day and amount, false otherwise
     */
    private boolean compareStatisticsEntries(StatisticsEntry entry1, StatisticsEntry entry2) {
        return entry1.getDay().toString().equals(entry2.getDay().toString()) && entry1.getAmount() == entry2.getAmount();
    }

    /**
     * Setup method in order to prevent code duplication for initialisation of entry list
     *
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
     *
     * @param principal the student login that should be used
     * @param date      the timestamp the login should be simulated
     * @return PersistentAuditEvent representing the login of the given user for at the given point in time
     */
    private PersistentAuditEvent setupPersistentEvent(String principal, ZonedDateTime date) {
        PersistentAuditEvent persistentEvent = new PersistentAuditEvent();
        persistentEvent.setPrincipal(principal);
        persistentEvent.setAuditEventType(AuditEventConstants.AUTHENTICATION_SUCCESS);
        persistentEvent.setAuditEventDate(Instant.from(date));

        return persistentEvent;
    }
}
