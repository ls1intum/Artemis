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

import de.tum.cit.aet.artemis.admin.domain.PersistentAuditEvent;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * Verifies against a real database which rows the retention queries select. The unit test covers the scheduling; this
 * covers the SQL, where the failure modes are silent: a row that is never selected is kept forever, and a row selected by
 * the wrong schedule is deleted years early. It also confirms that deleting a parent row removes its
 * {@code jhi_persistent_audit_evt_data} children, whose foreign key is {@code ON DELETE RESTRICT}.
 */
class AuditEventRetentionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "auditretention";

    private static final PageRequest BATCH = PageRequest.of(0, 100);

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @BeforeEach
    void removeExistingEvents() {
        // Other tests share this table, and these assertions are about exactly which rows a query returns.
        persistenceAuditEventRepository.deleteAll();
    }

    private PersistentAuditEvent addEvent(String type, Instant date) {
        PersistentAuditEvent event = new PersistentAuditEvent();
        event.setPrincipal(TEST_PREFIX + "-" + (type == null ? "null" : type));
        event.setAuditEventDate(date);
        event.setAuditEventType(type);
        Map<String, String> data = new HashMap<>();
        data.put("detail", "value");
        event.setData(data);
        return persistenceAuditEventRepository.save(event);
    }

    @Test
    void theShortScheduleSelectsOnlyExpiredLoginRecords() {
        Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        PersistentAuditEvent expiredLogin = addEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.minus(1, ChronoUnit.DAYS));
        PersistentAuditEvent expiredFailure = addEvent(AuditEventConstants.AUTHENTICATION_FAILURE, cutoff.minus(1, ChronoUnit.DAYS));
        addEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.plus(1, ChronoUnit.DAYS));
        addEvent(Constants.DELETE_EXERCISE, cutoff.minus(1, ChronoUnit.DAYS));

        List<Long> expired = persistenceAuditEventRepository.findExpiredIdsOfTypes(cutoff, AuditEventConstants.GENERAL_EVENT_TYPES, BATCH);

        // Not the recent login, and not the action event: that one is on the long schedule and is not expired yet.
        assertThat(expired).containsExactlyInAnyOrder(expiredLogin.getId(), expiredFailure.getId());
    }

    @Test
    void theLongScheduleSelectsEverythingThatIsNotALoginRecord() {
        Instant cutoff = Instant.now().minus(1825, ChronoUnit.DAYS);
        PersistentAuditEvent expiredAction = addEvent(Constants.DELETE_EXERCISE, cutoff.minus(1, ChronoUnit.DAYS));
        addEvent(Constants.DELETE_EXERCISE, cutoff.plus(1, ChronoUnit.DAYS));
        addEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.minus(1, ChronoUnit.DAYS));

        List<Long> expired = persistenceAuditEventRepository.findExpiredIdsExcludingTypes(cutoff, AuditEventConstants.GENERAL_EVENT_TYPES, BATCH);

        assertThat(expired).containsExactly(expiredAction.getId());
    }

    @Test
    void anEventWithoutATypeIsStillPruned() {
        // event_type is nullable, and "NOT IN (...)" is unknown rather than true for NULL, so a naive query would keep
        // these rows forever.
        Instant cutoff = Instant.now().minus(1825, ChronoUnit.DAYS);
        PersistentAuditEvent typeless = addEvent(null, cutoff.minus(1, ChronoUnit.DAYS));

        List<Long> expired = persistenceAuditEventRepository.findExpiredIdsExcludingTypes(cutoff, AuditEventConstants.GENERAL_EVENT_TYPES, BATCH);

        assertThat(expired).containsExactly(typeless.getId());
    }

    @Test
    void anEventTypeNobodyClassifiedGetsTheLongRetention() {
        // The safe direction for a type added later: over-retained rather than dropped on the short schedule.
        Instant shortCutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        PersistentAuditEvent unknown = addEvent("SOME_FUTURE_EVENT_NOBODY_CLASSIFIED", shortCutoff.minus(1, ChronoUnit.DAYS));

        assertThat(persistenceAuditEventRepository.findExpiredIdsOfTypes(shortCutoff, AuditEventConstants.GENERAL_EVENT_TYPES, BATCH)).isEmpty();
        assertThat(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(shortCutoff, AuditEventConstants.GENERAL_EVENT_TYPES, BATCH)).containsExactly(unknown.getId());
    }

    @Test
    void expiredEventsAreReturnedOldestFirstSoRepeatedRunsMakeProgress() {
        Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        PersistentAuditEvent newer = addEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, cutoff.minus(1, ChronoUnit.DAYS));
        PersistentAuditEvent oldest = addEvent(AuditEventConstants.AUTHENTICATION_FAILURE, cutoff.minus(400, ChronoUnit.DAYS));

        List<Long> expired = persistenceAuditEventRepository.findExpiredIdsOfTypes(cutoff, AuditEventConstants.GENERAL_EVENT_TYPES, BATCH);

        assertThat(expired).containsExactly(oldest.getId(), newer.getId());
    }

    @Test
    void deletingAnEventAlsoRemovesItsDataRows() {
        // The child table's foreign key is ON DELETE RESTRICT, so deleting through the entity is what makes this work; a
        // bulk DELETE on the parent table would be rejected by the database.
        PersistentAuditEvent event = addEvent(AuditEventConstants.AUTHENTICATION_SUCCESS, Instant.now().minus(400, ChronoUnit.DAYS));

        persistenceAuditEventRepository.deleteAllById(List.of(event.getId()));

        assertThat(persistenceAuditEventRepository.findById(event.getId())).isEmpty();
    }
}
