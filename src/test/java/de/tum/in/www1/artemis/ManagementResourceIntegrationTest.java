package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

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

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 2);
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
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getAllAuditEvents() throws Exception {
        var persAuditEvent = new PersistentAuditEvent();
        persAuditEvent.setPrincipal("student1");
        persAuditEvent.setAuditEventDate(Instant.now());
        persAuditEvent.setAuditEventType("type");
        persistenceAuditEventRepository.save(persAuditEvent);
        var auditEvents = request.getList("/management/audits", HttpStatus.OK, AuditEvent.class);
        var expectedAuditEvents = auditEventService.findAll(PageRequest.of(0, 20));
        assertThat(auditEvents).isEqualTo(expectedAuditEvents);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getAllAuditEventsByDate() throws Exception {
        LocalDate date = LocalDate.now();
        var auditEvents = request.getList("/management/audits?fromDate=2020-01-20&toDate=" + date.toString(), HttpStatus.OK, AuditEvent.class);
    }
}
