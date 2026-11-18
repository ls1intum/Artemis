package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import de.tum.cit.aet.artemis.admin.domain.ApplicationAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.PersistentAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.SecurityAuditEvent;
import de.tum.cit.aet.artemis.admin.repository.ApplicationAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * Verifies against a real database which rows each log's retention query selects. The unit test covers the scheduling
 * against mocks; this covers the SQL, where the failure modes are silent: a row that is never selected is kept forever,
 * and a row selected too eagerly is deleted years early.
 * <p>
 * Which log an event lands in is a separate question, covered by {@code AuditEventRoutingIntegrationTest} and
 * {@code AuditEventTypeClassifierTest}. After the split the retention query needs no type filter, because each table
 * holds exactly one retention class, which is the point of splitting them.
 * <p>
 * Deletion is asserted too: each log has an {@code @ElementCollection} child table, and the general log's foreign key is
 * {@code ON DELETE RESTRICT}, so deleting has to go through the entity rather than a bulk {@code DELETE} on the parent.
 */
class AuditEventRetentionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "auditretention";

    private static final PageRequest BATCH = PageRequest.of(0, 100);

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @Autowired
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Autowired
    private ApplicationAuditEventRepository applicationAuditEventRepository;

    @BeforeEach
    void removeExistingEvents() {
        // Other tests share these tables, and these assertions are about exactly which rows a query returns.
        persistenceAuditEventRepository.deleteAll();
        securityAuditEventRepository.deleteAll();
        applicationAuditEventRepository.deleteAll();
    }

    private static Map<String, String> someData() {
        Map<String, String> data = new HashMap<>();
        data.put("detail", "value");
        return data;
    }

    private PersistentAuditEvent addGeneralEvent(String type, Instant date) {
        PersistentAuditEvent event = new PersistentAuditEvent();
        event.setPrincipal(TEST_PREFIX + "-general-" + date.toEpochMilli());
        event.setAuditEventDate(date);
        event.setAuditEventType(type);
        event.setData(someData());
        return persistenceAuditEventRepository.save(event);
    }

    private SecurityAuditEvent addSecurityEvent(String type, Instant date) {
        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setPrincipal(TEST_PREFIX + "-security-" + date.toEpochMilli());
        event.setAuditEventDate(date);
        event.setAuditEventType(type);
        event.setData(someData());
        return securityAuditEventRepository.save(event);
    }

    private ApplicationAuditEvent addApplicationEvent(String type, Instant date) {
        ApplicationAuditEvent event = new ApplicationAuditEvent();
        event.setPrincipal(TEST_PREFIX + "-application-" + date.toEpochMilli());
        event.setAuditEventDate(date);
        event.setAuditEventType(type);
        event.setData(someData());
        return applicationAuditEventRepository.save(event);
    }

    @Test
    void theGeneralLogSelectsOnlyItsExpiredRows() {
        Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        PersistentAuditEvent expiredLogin = addGeneralEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.minus(1, ChronoUnit.DAYS));
        PersistentAuditEvent expiredFailure = addGeneralEvent(AuditEventConstants.AUTHENTICATION_FAILURE, cutoff.minus(2, ChronoUnit.DAYS));
        addGeneralEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.plus(1, ChronoUnit.DAYS));
        // An equally old row in another log must not be picked up: it has its own, longer retention.
        addApplicationEvent(Constants.DELETE_EXERCISE, cutoff.minus(1, ChronoUnit.DAYS));

        List<Long> expired = persistenceAuditEventRepository.findExpiredIds(cutoff, BATCH);

        assertThat(expired).containsExactlyInAnyOrder(expiredLogin.getId(), expiredFailure.getId());
    }

    @Test
    void theSecurityLogSelectsOnlyItsExpiredRows() {
        Instant cutoff = Instant.now().minus(1825, ChronoUnit.DAYS);
        SecurityAuditEvent expired = addSecurityEvent(AuditEventConstants.PASSWORD_RESET_COMPLETED, cutoff.minus(1, ChronoUnit.DAYS));
        addSecurityEvent(AuditEventConstants.PASSWORD_RESET_COMPLETED, cutoff.plus(1, ChronoUnit.DAYS));
        addGeneralEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.minus(1, ChronoUnit.DAYS));

        assertThat(securityAuditEventRepository.findExpiredIds(cutoff, BATCH)).containsExactly(expired.getId());
    }

    @Test
    void theApplicationLogSelectsOnlyItsExpiredRows() {
        Instant cutoff = Instant.now().minus(1825, ChronoUnit.DAYS);
        ApplicationAuditEvent expiredAction = addApplicationEvent(Constants.DELETE_EXERCISE, cutoff.minus(1, ChronoUnit.DAYS));
        addApplicationEvent(Constants.DELETE_EXERCISE, cutoff.plus(1, ChronoUnit.DAYS));
        addGeneralEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.minus(1, ChronoUnit.DAYS));

        assertThat(applicationAuditEventRepository.findExpiredIds(cutoff, BATCH)).containsExactly(expiredAction.getId());
    }

    @Test
    void expiredEventsAreReturnedOldestFirstSoRepeatedRunsMakeProgress() {
        // The prune loop always asks for the first page, so without this ordering a large backlog could revisit the same
        // rows instead of advancing through it.
        Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        PersistentAuditEvent newer = addGeneralEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.minus(1, ChronoUnit.DAYS));
        PersistentAuditEvent oldest = addGeneralEvent(AuditEventConstants.AUTHENTICATION_FAILURE, cutoff.minus(400, ChronoUnit.DAYS));

        assertThat(persistenceAuditEventRepository.findExpiredIds(cutoff, BATCH)).containsExactly(oldest.getId(), newer.getId());
    }

    @Test
    void deletingAnEventFromEachLogAlsoRemovesItsDataRows() {
        // The child tables' foreign keys make this the only working shape: deleting through the entity cascades to the
        // data rows, whereas a bulk DELETE on the parent table would be rejected by the database.
        Instant longExpired = Instant.now().minus(2000, ChronoUnit.DAYS);
        PersistentAuditEvent general = addGeneralEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, longExpired);
        SecurityAuditEvent security = addSecurityEvent(AuditEventConstants.PASSWORD_RESET_COMPLETED, longExpired);
        ApplicationAuditEvent application = addApplicationEvent(Constants.DELETE_EXERCISE, longExpired);

        persistenceAuditEventRepository.deleteAllById(List.of(general.getId()));
        securityAuditEventRepository.deleteAllById(List.of(security.getId()));
        applicationAuditEventRepository.deleteAllById(List.of(application.getId()));

        assertThat(persistenceAuditEventRepository.findById(general.getId())).isEmpty();
        assertThat(securityAuditEventRepository.findById(security.getId())).isEmpty();
        assertThat(applicationAuditEventRepository.findById(application.getId())).isEmpty();
    }
}
