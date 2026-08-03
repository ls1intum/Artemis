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

import de.tum.cit.aet.artemis.admin.repository.ApplicationAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.config.audit.AuditLogType;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies end to end, against a real database, that an audit event is written to the table its type belongs to and that
 * it can be read back through {@link AuditEventService} under the matching {@link AuditLogType}. This is the behaviour the
 * three admin tabs and the three retention periods both depend on.
 */
class AuditEventRoutingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "auditrouting";

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

        List<AuditEvent> securityEvents = auditEventService.findAll(AuditLogType.SECURITY, PageRequest.of(0, 100)).getContent();
        assertThat(securityEvents).anySatisfy(event -> {
            assertThat(event.getType()).isEqualTo(AuditEventConstants.ACCOUNT_EMAIL_CHANGED);
            assertThat(event.getPrincipal()).isEqualTo(TEST_PREFIX + "-" + AuditEventConstants.ACCOUNT_EMAIL_CHANGED);
            // The key/value child table has to round-trip too, since the admin view renders it.
            assertThat(event.getData()).containsEntry("detail", "value");
        });

        // ... and must not leak into another tab's log.
        assertThat(auditEventService.findAll(AuditLogType.GENERAL, PageRequest.of(0, 100)).getContent())
                .noneMatch(event -> AuditEventConstants.ACCOUNT_EMAIL_CHANGED.equals(event.getType()));
    }

    @Test
    void findByDatesIsScopedToTheRequestedLog() {
        addEvent(Constants.RESET_EXAM);

        Instant from = Instant.now().minusSeconds(300);
        Instant to = Instant.now().plusSeconds(300);

        assertThat(auditEventService.findByDates(AuditLogType.APPLICATION, from, to, PageRequest.of(0, 100)).getContent())
                .anyMatch(event -> Constants.RESET_EXAM.equals(event.getType()));
        assertThat(auditEventService.findByDates(AuditLogType.SECURITY, from, to, PageRequest.of(0, 100)).getContent())
                .noneMatch(event -> Constants.RESET_EXAM.equals(event.getType()));
    }
}
