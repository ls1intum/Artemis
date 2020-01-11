package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.SingleUserNotification;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class NotificationResourceIntegrationTest extends AbstractSpringIntegrationTest {
    User user;

    Notification notification;

    @Autowired
    RequestUtilService request;


    @Autowired
    NotificationRepository notificationRepository;

    @BeforeEach
    public void initTestCase() throws Exception {
        user = ModelFactory.generateActivatedUser("");
        notification = new SingleUserNotification().recipient(user).title("test title").text("test test");
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testCreateNotification_asUser() throws Exception {
        request.post("/api/notifications", notification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testCreateNotification_asInstructor() throws Exception {
        request.post("/api/notifications", notification, HttpStatus.CREATED);
    }
}
