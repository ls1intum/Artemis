package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

        List<AuditEvent> securityEvents = auditEventService.findAll(AuditLogType.SECURITY, NEWEST_FIRST).getContent();
        assertThat(securityEvents).anySatisfy(event -> {
            assertThat(event.getType()).isEqualTo(AuditEventConstants.ACCOUNT_EMAIL_CHANGED);
            assertThat(event.getPrincipal()).isEqualTo(TEST_PREFIX + "-" + AuditEventConstants.ACCOUNT_EMAIL_CHANGED);
            // The key/value child table has to round-trip too, since the admin view renders it.
            assertThat(event.getData()).containsEntry("detail", "value");
        });

        // ... and must not leak into another tab's log.
        assertThat(auditEventService.findAll(AuditLogType.GENERAL, NEWEST_FIRST).getContent())
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

        assertThat(auditEventService.findByDates(AuditLogType.APPLICATION, from, to, NEWEST_FIRST).getContent()).anyMatch(event -> Constants.RESET_EXAM.equals(event.getType()));
        assertThat(auditEventService.findByDates(AuditLogType.SECURITY, from, to, NEWEST_FIRST).getContent()).noneMatch(event -> Constants.RESET_EXAM.equals(event.getType()));
    }
}
