package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.authentication.AuthenticationFactory;
import de.tum.cit.aet.artemis.admin.repository.ApplicationAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.config.audit.AuditLogType;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * Verifies end to end, against a real database, that an audit event is written to the table its type belongs to and that
 * it can be read back through {@link AuditEventService} under the matching {@link AuditLogType}. This is the behaviour the
 * three admin tabs and the three retention periods both depend on.
 */
class AuditEventRoutingIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "auditrouting";

    /**
     * Without an explicit order the database may return any 100 rows, so an event created by this test could fall outside
     * the first page once a log holds more than a page of rows.
     */
    private static final PageRequest NEWEST_FIRST = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "auditEventDate"));

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @Autowired
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Autowired
    private ApplicationAuditEventRepository applicationAuditEventRepository;

    private long generalCountBefore;

    private long securityCountBefore;

    private long applicationCountBefore;

    @BeforeEach
    void recordCountsBefore() {
        // Other tests in the same database may have written events, so assert on deltas rather than absolute counts.
        generalCountBefore = persistenceAuditEventRepository.count();
        securityCountBefore = securityAuditEventRepository.count();
        applicationCountBefore = applicationAuditEventRepository.count();
    }

    private void addEvent(String type) {
        auditEventRepository.add(new AuditEvent(Instant.now(), TEST_PREFIX + "-" + type, type, Map.of("detail", "value")));
    }

    @Test
    void authenticationEventGoesToTheGeneralLogOnly() {
        addEvent(AuditEventConstants.AUTHENTICATION_SUCCESS);

        assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore + 1);
        assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore);
        assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore);
    }

    @Test
    void securityEventGoesToTheSecurityLogOnly() {
        addEvent(AuditEventConstants.PASSWORD_RESET_COMPLETED);

        assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore + 1);
        assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore);
        assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore);
    }

    @Test
    void domainEventGoesToTheApplicationLogOnly() {
        addEvent(Constants.DELETE_EXERCISE);

        assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore + 1);
        assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore);
        assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore);
    }

    @Test
    void attendanceUpdateByPasskeyAuthenticatedInstructorStaysInApplicationLog() {
        var previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        String instructor = TEST_PREFIX + "-instructor";
        Instant before = Instant.now().minusSeconds(1);
        try {
            SecurityContextHolder.getContext().setAuthentication(AuthenticationFactory.createWebAuthnAuthentication(instructor));
            auditEventRepository.add(new AuditEvent(instructor, Constants.UPDATE_EXAM_ATTENDANCE, Map.of("examId", "42", "oldDidCheckName", "false", "newDidCheckName", "true")));

            assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore + 1);
            assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore);
            assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore);
            assertThat(auditEventRepository.find(instructor, before, Constants.UPDATE_EXAM_ATTENDANCE)).singleElement().satisfies(event -> {
                assertThat(event.getPrincipal()).isEqualTo(instructor);
                assertThat(event.getData()).containsEntry("examId", "42").containsEntry("oldDidCheckName", "false").containsEntry("newDidCheckName", "true");
            });
        }
        finally {
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    @Test
    void passkeyAuthenticationSuccessRetainsItsLoginEventType() {
        var previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        String instructor = TEST_PREFIX + "-passkey-login";
        Instant before = Instant.now().minusSeconds(1);
        try {
            SecurityContextHolder.getContext().setAuthentication(AuthenticationFactory.createWebAuthnAuthentication(instructor));
            auditEventRepository.add(new AuditEvent(instructor, AuditEventConstants.AUTHENTICATION_SUCCESS));

            assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore + 1);
            assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore);
            assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore);
            assertThat(auditEventRepository.find(instructor, before, AuditEventConstants.AUTHENTICATION_PASSKEY_SUCCESS)).hasSize(1);
        }
        finally {
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    @Test
    void unknownEventTypeGoesToTheApplicationLog() {
        addEvent("SOME_UNCLASSIFIED_EVENT");

        assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore + 1);
        assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore);
        assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore);
    }

    @Test
    void authorizationFailureIsNotPersistedInAnyLog() {
        addEvent(AuditEventConstants.AUTHORIZATION_FAILURE);

        assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore);
        assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore);
        assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore);
    }

    @Test
    void eventsAreReadBackUnderTheirOwnLogTypeWithTheirDataPreserved() {
        addEvent(AuditEventConstants.ACCOUNT_EMAIL_CHANGED);

        List<AuditEvent> securityEvents = auditEventService.findAll(AuditLogType.SECURITY, null, NEWEST_FIRST).getContent();
        assertThat(securityEvents).anySatisfy(event -> {
            assertThat(event.getType()).isEqualTo(AuditEventConstants.ACCOUNT_EMAIL_CHANGED);
            assertThat(event.getPrincipal()).isEqualTo(TEST_PREFIX + "-" + AuditEventConstants.ACCOUNT_EMAIL_CHANGED);
            // The key/value child table has to round-trip too, since the admin view renders it.
            assertThat(event.getData()).containsEntry("detail", "value");
        });

        // ... and must not leak into another tab's log.
        assertThat(auditEventService.findAll(AuditLogType.GENERAL, null, NEWEST_FIRST).getContent())
                .noneMatch(event -> AuditEventConstants.ACCOUNT_EMAIL_CHANGED.equals(event.getType()));
    }

    @Test
    void failedLoginGoesToTheGeneralLog() {
        // Failed logins are part of the login record: they belong with the successful ones, under the short retention,
        // and must stay visible in the Login tab rather than being treated as an unrecognised application event.
        addEvent(AuditEventConstants.AUTHENTICATION_FAILURE);

        assertThat(persistenceAuditEventRepository.count()).isEqualTo(generalCountBefore + 1);
        assertThat(securityAuditEventRepository.count()).isEqualTo(securityCountBefore);
        assertThat(applicationAuditEventRepository.count()).isEqualTo(applicationCountBefore);
    }

    @Test
    void findRetrievesEventsFromWhicheverLogHoldsTheType() {
        // Spring Boot's AuditEventRepository contract is also used directly (e.g. by IrisChatSessionResource), so a
        // lookup has to reach the log that add() routed the event to, not just the general one.
        Instant before = Instant.now().minusSeconds(60);
        for (String type : List.of(AuditEventConstants.AUTHENTICATION_FAILURE, AuditEventConstants.PASSWORD_RESET_COMPLETED, Constants.DELETE_EXERCISE)) {
            // A principal unique to this test, because the other tests share the database and write the same types.
            String principal = TEST_PREFIX + "-find-" + type;
            auditEventRepository.add(new AuditEvent(Instant.now(), principal, type, Map.of("detail", "value")));

            assertThat(auditEventRepository.find(principal, before, type)).as("lookup of %s", type).singleElement()
                    .satisfies(event -> assertThat(event.getData()).containsEntry("detail", "value"));
        }
    }

    @Test
    void findWithoutATypeFilterSearchesEveryLog() {
        String principal = TEST_PREFIX + "-untyped-lookup";
        Instant before = Instant.now().minusSeconds(60);
        for (String type : List.of(AuditEventConstants.AUTHENTICATION_FAILURE, AuditEventConstants.PASSWORD_RESET_COMPLETED, Constants.DELETE_EXERCISE)) {
            auditEventRepository.add(new AuditEvent(Instant.now(), principal, type, Map.of("detail", "value")));
        }

        // Ordered by time, not by which log a row happens to live in: the three events were written in this order.
        assertThat(auditEventRepository.find(principal, before, null)).extracting(AuditEvent::getType).containsExactly(AuditEventConstants.AUTHENTICATION_FAILURE,
                AuditEventConstants.PASSWORD_RESET_COMPLETED, Constants.DELETE_EXERCISE);
    }

    @Test
    void findByDatesIsScopedToTheRequestedLog() {
        addEvent(Constants.RESET_EXAM);

        Instant from = Instant.now().minusSeconds(300);
        Instant to = Instant.now().plusSeconds(300);

        assertThat(auditEventService.findByDates(AuditLogType.APPLICATION, null, from, to, NEWEST_FIRST).getContent())
                .anyMatch(event -> Constants.RESET_EXAM.equals(event.getType()));
        assertThat(auditEventService.findByDates(AuditLogType.SECURITY, null, from, to, NEWEST_FIRST).getContent())
                .noneMatch(event -> Constants.RESET_EXAM.equals(event.getType()));
    }

    /**
     * The logins and the time of the events of one test. The names are fixed per test and log and carry no random part, because every test runs once in a database of its own
     * and only the events of its own logins count in it. The time is taken once, so that the range assertions follow from it.
     */
    private record SearchFixture(String searched, String other, Instant eventTime) {

        static SearchFixture of(String test, AuditLogType logType) {
            String suffix = test + "-" + logType.name().toLowerCase(Locale.ROOT);
            return new SearchFixture("auditsearch-a-" + suffix, "auditsearch-b-" + suffix, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    private static String eventTypeOf(AuditLogType logType) {
        return switch (logType) {
            case GENERAL -> AuditEventConstants.AUTHENTICATION_SUCCESS;
            case SECURITY -> AuditEventConstants.PASSWORD_RESET_COMPLETED;
            case APPLICATION -> Constants.DELETE_EXERCISE;
        };
    }

    private void addEvents(AuditLogType logType, String principal, Instant eventTime, int count) {
        for (int i = 0; i < count; i++) {
            auditEventRepository.add(new AuditEvent(eventTime.minusSeconds(i), principal, eventTypeOf(logType), Map.of("detail", "value")));
        }
    }

    /** Two events of each of the two logins of the fixture in the given log. */
    private SearchFixture addEventsOfTwoPrincipals(String test, AuditLogType logType) {
        SearchFixture fixture = SearchFixture.of(test, logType);
        addEvents(logType, fixture.searched(), fixture.eventTime(), 2);
        addEvents(logType, fixture.other(), fixture.eventTime(), 2);
        return fixture;
    }

    @ParameterizedTest
    @EnumSource(AuditLogType.class)
    void searchByPrincipalReturnsOnlyTheEventsOfThatPrincipalInTheRequestedLog(AuditLogType logType) {
        SearchFixture fixture = addEventsOfTwoPrincipals("only-that-login", logType);

        var page = auditEventService.findAll(logType, fixture.searched(), NEWEST_FIRST);

        assertThat(page.getContent()).hasSize(2).allMatch(event -> fixture.searched().equals(event.getPrincipal()));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(AuditLogType.class)
    void searchByPrincipalTrimsAndLowerCasesTheTypedTerm(AuditLogType logType) {
        SearchFixture fixture = addEventsOfTwoPrincipals("trim-lower-case", logType);

        // Logins are stored in lower case, so a term typed in upper case with spaces around it finds them
        var page = auditEventService.findAll(logType, "  " + fixture.searched().toUpperCase(Locale.ROOT) + " ", NEWEST_FIRST);

        assertThat(page.getContent()).hasSize(2).allMatch(event -> fixture.searched().equals(event.getPrincipal()));
    }

    @ParameterizedTest
    @EnumSource(AuditLogType.class)
    void searchByPrincipalFindsAFailedAttemptThatKeptTheLoginAsItWasTyped(AuditLogType logType) {
        SearchFixture fixture = SearchFixture.of("typed-spelling", logType);
        String typedLogin = "AuditSearch-Typed-" + logType.name();
        addEvents(logType, typedLogin, fixture.eventTime(), 1);
        addEvents(logType, typedLogin.toLowerCase(Locale.ROOT), fixture.eventTime(), 1);

        // Typed as it is recorded, the term finds both spellings of the login
        assertThat(auditEventService.findAll(logType, typedLogin, NEWEST_FIRST).getContent()).extracting(AuditEvent::getPrincipal).containsExactlyInAnyOrder(typedLogin,
                typedLogin.toLowerCase(Locale.ROOT));
        // Typed in lower case, it finds the lower case one only: the comparison stays an equality, which keeps the search on the index
        assertThat(auditEventService.findAll(logType, typedLogin.toLowerCase(Locale.ROOT), NEWEST_FIRST).getContent()).extracting(AuditEvent::getPrincipal)
                .containsExactly(typedLogin.toLowerCase(Locale.ROOT));
    }

    @ParameterizedTest
    @EnumSource(AuditLogType.class)
    void searchByPrincipalMatchesTheWholeLoginOnly(AuditLogType logType) {
        SearchFixture fixture = addEventsOfTwoPrincipals("whole-login", logType);

        // Exact match is what keeps the search on the index, so a part of the login finds nothing
        String partOfTheLogin = fixture.searched().substring(0, fixture.searched().length() - 3);
        assertThat(auditEventService.findAll(logType, partOfTheLogin, NEWEST_FIRST).getContent()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(AuditLogType.class)
    void searchByPrincipalCanBeCombinedWithTheDateRange(AuditLogType logType) {
        SearchFixture fixture = addEventsOfTwoPrincipals("date-range", logType);

        Instant from = fixture.eventTime().minusSeconds(300);
        Instant to = fixture.eventTime().plusSeconds(300);

        var inRange = auditEventService.findByDates(logType, fixture.searched(), from, to, NEWEST_FIRST);
        assertThat(inRange.getContent()).hasSize(2).allMatch(event -> fixture.searched().equals(event.getPrincipal()));
        assertThat(inRange.getTotalElements()).isEqualTo(2);

        // the events exist, but not in this range
        assertThat(auditEventService.findByDates(logType, fixture.searched(), from.minusSeconds(7200), from.minusSeconds(3600), NEWEST_FIRST).getContent()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(AuditLogType.class)
    void aPageAfterTheLastOneIsEmptyButKeepsTheTotal(AuditLogType logType) {
        SearchFixture fixture = addEventsOfTwoPrincipals("page-after-last", logType);
        PageRequest secondPage = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "auditEventDate"));
        Instant from = fixture.eventTime().minusSeconds(300);
        Instant to = fixture.eventTime().plusSeconds(300);

        // The two events fill the first page, so the second is empty. The total must still say 2, or the client would show a wrong last page.
        var searched = auditEventService.findAll(logType, fixture.searched(), secondPage);
        assertThat(searched.getContent()).isEmpty();
        assertThat(searched.getTotalElements()).isEqualTo(2);

        var searchedInRange = auditEventService.findByDates(logType, fixture.searched(), from, to, secondPage);
        assertThat(searchedInRange.getContent()).isEmpty();
        assertThat(searchedInRange.getTotalElements()).isEqualTo(2);

        // and so does a page of the log without a search
        long inLog = auditEventService.findByDates(logType, null, from, to, PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "auditEventDate"))).getTotalElements();
        var afterLast = auditEventService.findByDates(logType, null, from, to, PageRequest.of(1000, 1, Sort.by(Sort.Direction.DESC, "auditEventDate")));
        assertThat(afterLast.getContent()).isEmpty();
        assertThat(afterLast.getTotalElements()).isEqualTo(inLog);
    }

    @ParameterizedTest
    @EnumSource(AuditLogType.class)
    void aBlankSearchTermReturnsTheEventsOfEveryPrincipal(AuditLogType logType) {
        SearchFixture fixture = addEventsOfTwoPrincipals("blank-term", logType);

        assertThat(auditEventService.findAll(logType, "   ", NEWEST_FIRST).getContent()).extracting(AuditEvent::getPrincipal).contains(fixture.searched(), fixture.other());
        assertThat(auditEventService.findAll(logType, null, NEWEST_FIRST).getContent()).extracting(AuditEvent::getPrincipal).contains(fixture.searched(), fixture.other());
    }
}
