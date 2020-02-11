package de.tum.in.www1.artemis;

import static org.mockito.Mockito.verify;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ManagementResourceIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 2);
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
        var auditEvents = request.getList("/management/audits", HttpStatus.OK, AuditEvent.class);
    }
}
