package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.audit.AuditEventConverter;
import de.tum.in.www1.artemis.domain.PersistentAuditEvent;
import de.tum.in.www1.artemis.repository.PersistenceAuditEventRepository;
import de.tum.in.www1.artemis.service.AuditEventService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ManagementResourceIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    AuditEventService auditEventService;

    @Autowired
    PersistenceAuditEventRepository persistenceAuditEventRepository;

    @Autowired
    AuditEventConverter auditEventConverter;

    private PersistentAuditEvent persAuditEvent;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 0, 0);
        persAuditEvent = new PersistentAuditEvent();
        persAuditEvent.setPrincipal("student1");
        persAuditEvent.setAuditEventDate(Instant.now());
        persAuditEvent.setAuditEventType("type");
        var data = new HashMap<String, String>();
        data.put("1", "2");
        persAuditEvent.setData(data);
        persAuditEvent = persistenceAuditEventRepository.save(persAuditEvent);

        var persAuditEvent2 = new PersistentAuditEvent();
        persAuditEvent2.setPrincipal("student2");
        persAuditEvent2.setAuditEventDate(Instant.now().minus(5, ChronoUnit.DAYS));
        persAuditEvent2.setAuditEventType("tt");
        persAuditEvent2.setData(data);
        persistenceAuditEventRepository.save(persAuditEvent2);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void toggleFeatures() throws Exception {
        var features = new HashMap<Feature, Boolean>();
        features.put(Feature.PROGRAMMING_EXERCISES, false);
        request.put("/api/management/feature-toggle", features, HttpStatus.OK);
        verify(this.websocketMessagingService).sendMessage("/topic/management/feature-toggles", Feature.enabledFeatures());
        assertThat(Feature.PROGRAMMING_EXERCISES.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getAllAuditEvents() throws Exception {
        var auditEvents = request.getList("/management/audits", HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(auditEvents.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getAllAuditEventsByDate() throws Exception {
        String pastDate = LocalDate.now().minusDays(2).toString();
        String currentDate = LocalDate.now().toString();
        var auditEvents = request.getList("/management/audits?fromDate=" + pastDate + "&toDate=" + currentDate, HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(auditEvents.size()).isEqualTo(1);
        var auditEvent = auditEvents.get(0);
        var auditEventsInDb = persistenceAuditEventRepository.findAllByAuditEventDateBetween(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now(), Pageable.unpaged());
        assertThat(auditEventsInDb.getTotalElements()).isEqualTo(1);
        assertThat(auditEvent.getPrincipal()).isEqualTo(auditEventsInDb.get().findFirst().get().getPrincipal());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getAuditEvent() throws Exception {
        var auditEvent = request.get("/management/audits/" + persAuditEvent.getId(), HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(auditEvent).isNotNull();
        var auditEventInDb = persistenceAuditEventRepository.findById(persAuditEvent.getId()).get();
        assertThat(auditEventInDb.getPrincipal()).isEqualTo(auditEvent.getPrincipal());
    }
}
