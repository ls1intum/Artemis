package de.tum.in.www1.artemis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.SingleUserNotification;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class NotificationResourceIntegrationTest {

    private Notification notification;

    @Autowired
    RequestUtilService request;

    @BeforeEach
    public void init() throws Exception {
        notification = new SingleUserNotification();
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testCreateNotification_asUser() throws Exception {
        request.post("/api/notifications", notification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testCreateNotification_as() throws Exception {
        request.post("/api/notifications", notification, HttpStatus.CREATED);
    }
}
