package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;

import de.tum.in.www1.artemis.domain.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class SystemNotificationIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    SystemNotificationRepository systemNotificationRepo;

    @BeforeEach
    @Sql({ "/h2/custom-functions.sql" })
    public void initTestCase() {
        // Generate a system notification that has expired.
        SystemNotification systemNotificationExpired = ModelFactory.generateSystemNotification((ZonedDateTime.now().minusDays(5)), (ZonedDateTime.now().minusDays(8)));
        systemNotificationRepo.save(systemNotificationExpired);

        // Generate a system notification whose notification date is in the future.
        SystemNotification systemNotificationFuture = ModelFactory.generateSystemNotification((ZonedDateTime.now().plusDays(8)), (ZonedDateTime.now().plusDays(5)));
        systemNotificationRepo.save(systemNotificationFuture);

        // Generate an active system notification
        SystemNotification systemNotificationActive = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        systemNotificationRepo.save(systemNotificationActive);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @Sql({ "/h2/custom-functions.sql" })
    public void getActiveSystemNotification() throws Exception {
        // Do the actual request that is tested here.
        SystemNotification systemNotification = request.get("/api/system-notifications/active-notification", HttpStatus.OK, SystemNotification.class);

        // The returned notification must be an active notification.
        assertThat(systemNotification.getExpireDate()).as("Returned notification has not expired yet.").isAfterOrEqualTo(ZonedDateTime.now());
        assertThat(systemNotification.getNotificationDate()).as("Returned notification is active.").isBeforeOrEqualTo(ZonedDateTime.now());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateSystemNotification() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        request.post("/api/system-notifications", systemNotification, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateSystemNotification_BadRequest() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        systemNotification.setId(1L);
        request.post("/api/system-notifications", systemNotification, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testCreateSystemNotification_Forbidden() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        systemNotification.setId(1L);
        request.post("/api/system-notifications", systemNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateSystemNotification() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        systemNotification.setId(1L);
        systemNotificationRepo.save(systemNotification);
        systemNotification.setText("updated text");
        request.put("/api/system-notifications", systemNotification, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateSystemNotification_BadRequest() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        request.put("/api/system-notifications", systemNotification, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllSystemNotifications() throws Exception {
        request.get("/api/system-notifications", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetSystemNotification() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        SystemNotification response = request.postWithResponseBody("/api/system-notifications", systemNotification, SystemNotification.class, HttpStatus.CREATED);
        request.get("/system-notifications/" + response.getId() + 1, HttpStatus.NOT_FOUND, SystemNotification.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteSystemNotification() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(3), ZonedDateTime.now().minusDays(3));
        SystemNotification response = request.postWithResponseBody("/api/system-notifications", systemNotification, SystemNotification.class, HttpStatus.CREATED);
        request.delete("/system-notifications/" + response.getId() + 1, HttpStatus.NOT_FOUND);
    }
}
